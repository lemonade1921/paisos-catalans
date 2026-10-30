package cat.paisoscatalans.compartir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.content.FileProvider
import cat.paisoscatalans.data.Article
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.Progres
import cat.paisoscatalans.ui.mapa.ModelMapa
import cat.paisoscatalans.ui.milers
import cat.paisoscatalans.ui.percent
import java.io.File
import kotlin.math.min

enum class Format(val etiqueta: String, val amplada: Int, val alcada: Int) {
    HISTORIA("Història", 1080, 1920),
    PUBLICACIO("Publicació", 1080, 1080),
    TEXT("Text", 0, 0),
}

data class Opcions(val format: Format, val mapa: Boolean, val comarques: Boolean)

/** Destinacions. Totes funcionen amb intents locals d'Android: l'app no es connecta enlloc. */
enum class Desti(val etiqueta: String, val paquets: List<String>, val nomesImatge: Boolean = false) {
    INSTAGRAM("Instagram Stories", listOf("com.instagram.android"), nomesImatge = true),
    SIGNAL("Signal", listOf("org.thoughtcrime.securesms")),
    MOLLY("Molly", listOf("im.molly.app")),
    MASTODON(
        "Mastodon",
        listOf(
            "org.joinmastodon.android", "org.joinmastodon.android.moshinda", "com.keylesspalace.tusky",
            "app.fedilab.android", "fr.gouv.etalab.mastodon", "social.tooot",
        ),
    ),
    BLUESKY("Bluesky", listOf("xyz.blueskyweb.app")),
}

object Compartir {
    private const val MARRO = 0xFF9A4521.toInt()
    private const val MARRO_CLAR = 0xFFC2693F.toInt()
    private const val GROC = 0xFFF1E48A.toInt()
    private const val BLANC = 0xFFFFFFFF.toInt()
    private const val FOSC = 0xFF201C00.toInt()

    fun text(geo: Geo, p: Progres, comarques: Boolean): String = buildString {
        val n = p.municipisVisitats.size
        append("He visitat $n dels ${milers(geo.municipis.size)} municipis dels Països Catalans (${percent(n, geo.municipis.size)})")
        if (comarques) {
            append(", ${p.comarquesVisitades.size} comarques i ${p.vegueriesVisitades.size} vegueries")
        }
        append(".\n#PaïsosCatalans")
    }

    fun paquetInstallat(ctx: Context, d: Desti): String? = d.paquets.firstOrNull { paquet ->
        try {
            ctx.packageManager.getPackageInfo(paquet, 0); true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun renderitza(geo: Geo, model: ModelMapa, p: Progres, nom: String, article: String, o: Opcions): Bitmap? {
        if (o.format == Format.TEXT) return null
        val w = o.format.amplada
        val h = o.format.alcada
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(MARRO)
        val negre = Typeface.create(Typeface.DEFAULT, 900, false)
        val semi = Typeface.create(Typeface.DEFAULT, 600, false)
        fun paint(mida: Float, color: Int, tf: Typeface, espai: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = mida; this.color = color; typeface = tf; letterSpacing = espai
        }
        val n = p.municipisVisitats.size
        val capcalera = if (nom.isNotBlank()) "EL MAPA ${Article.genitiu(nom, article).uppercase()}" else "PAÏSOS CATALANS"

        if (o.format == Format.HISTORIA) {
            c.drawText(capcalera, 96f, 170f, paint(42f, BLANC, semi, 0.12f))
            if (o.mapa) dibuixaMapa(c, model, p, Rect(80f, 240f, w - 80f, 1260f))
            c.drawText(milers(n), 90f, 1530f, paint(260f, GROC, negre, -0.03f))
            c.drawText("municipis visitats · ${percent(n, geo.municipis.size)}", 96f, 1610f, paint(52f, BLANC, semi))
            if (o.comarques) {
                c.drawText("${p.comarquesVisitades.size} comarques · ${p.vegueriesVisitades.size} vegueries",
                    96f, 1690f, paint(44f, BLANC, semi))
            }
            c.drawText("#PaïsosCatalans", 96f, 1830f, paint(40f, GROC, semi))
        } else {
            c.drawText(capcalera, 72f, 120f, paint(34f, BLANC, semi, 0.12f))
            val esquerra = if (o.mapa) 470f else w - 72f
            c.drawText(milers(n), 64f, 600f, paint(if (o.mapa) 190f else 260f, GROC, negre, -0.03f))
            c.drawText("municipis · ${percent(n, geo.municipis.size)}", 72f, 680f, paint(44f, BLANC, semi))
            if (o.comarques) {
                c.drawText("${p.comarquesVisitades.size} comarques", 72f, 750f, paint(38f, BLANC, semi))
                c.drawText("${p.vegueriesVisitades.size} vegueries", 72f, 805f, paint(38f, BLANC, semi))
            }
            c.drawText("#PaïsosCatalans", 72f, 990f, paint(34f, GROC, semi))
            if (o.mapa) dibuixaMapa(c, model, p, Rect(esquerra, 160f, w - 40f, h - 60f))
        }
        return bmp
    }

    private fun dibuixaMapa(c: Canvas, model: ModelMapa, p: Progres, area: Rect) {
        val r = model.limits
        val k = min(area.width / r.width, area.height / r.height)
        val fons = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MARRO_CLAR; style = Paint.Style.FILL }
        val vist = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GROC; style = Paint.Style.FILL }
        val vora = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARRO; style = Paint.Style.STROKE; strokeWidth = 1.2f / k
        }
        c.save()
        c.translate(area.center.x - r.center.x * k, area.center.y - r.center.y * k)
        c.scale(k, k)
        for (e in model.municipis) c.drawPath(e.path.asAndroidPath(), if (e.id in p.municipisVisitats) vist else fons)
        for (e in model.comarques) c.drawPath(e.path.asAndroidPath(), vora)
        model.requadreAlguer?.let {
            c.drawRoundRect(it.left, it.top, it.right, it.bottom, it.width * 0.18f, it.width * 0.18f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GROC; style = Paint.Style.STROKE; strokeWidth = 2f / k })
        }
        c.restore()
    }

    private fun desaTemporal(ctx: Context, bmp: Bitmap): Uri {
        val dir = File(ctx.cacheDir, "compartir").apply { mkdirs() }
        val f = File(dir, "paisos-catalans.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".fitxers", f)
    }

    /** Envia a la destinació. Retorna un missatge d'error si no s'ha pogut. */
    fun envia(ctx: Context, d: Desti?, bmp: Bitmap?, text: String): String? {
        val uri = bmp?.let { desaTemporal(ctx, it) }
        val paquet = d?.let { paquetInstallat(ctx, it) }

        if (d == Desti.INSTAGRAM && uri == null) return "Instagram necessita una imatge: tria Història o Publicació"

        if (d != null && paquet == null) {
            if (d == Desti.BLUESKY) {
                // Sense l'app: obre el formulari web de Bluesky al navegador (només text).
                val url = "https://bsky.app/intent/compose?text=" + Uri.encode(text)
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return null
            }
            if (d != Desti.MASTODON) return "${d.etiqueta} no està instal·lada"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            if (uri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            if (d != Desti.INSTAGRAM) putExtra(Intent.EXTRA_TEXT, text)
            if (paquet != null) setPackage(paquet)
        }
        return try {
            if (paquet != null) ctx.startActivity(intent)
            else ctx.startActivity(Intent.createChooser(intent, d?.etiqueta ?: "Comparteix"))
            null
        } catch (e: Exception) {
            "No s'ha pogut obrir: ${e.localizedMessage}"
        }
    }

    fun desaGaleria(ctx: Context, bmp: Bitmap): Boolean = try {
        val valors = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "paisos-catalans-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PaisosCatalans")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valors)
        uri != null && ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
    } catch (_: Exception) {
        false
    }

    fun copia(ctx: Context, text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Països Catalans", text))
    }
}
