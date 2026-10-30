package cat.paisoscatalans.compartir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Telephony
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

/** Colors de la imatge compartida. */
enum class Paleta(
    val etiqueta: String,
    val fons: Int,
    val mapa: Int,
    val visitat: Int,
    val text: Int,
    val accent: Int,
) {
    TERRACOTA("Terracota", 0xFF9A4521.toInt(), 0xFFC2693F.toInt(), 0xFFF1E48A.toInt(), 0xFFFFFFFF.toInt(), 0xFFF1E48A.toInt()),
    MAR("Mar", 0xFF0B4F6C.toInt(), 0xFF2B7395.toInt(), 0xFFFFD166.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD166.toInt()),
    MENTA("Menta", 0xFF1F6F5C.toInt(), 0xFF3E8C77.toInt(), 0xFFFFE08A.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE08A.toInt()),
    OLIVERA("Olivera", 0xFF4F5D2F.toInt(), 0xFF6F8046.toInt(), 0xFFF4E3B2.toInt(), 0xFFFFFFFF.toInt(), 0xFFF4E3B2.toInt()),
    LAVANDA("Lavanda", 0xFF5B3E8A.toInt(), 0xFF7B5EAB.toInt(), 0xFFFFD6E8.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD6E8.toInt()),
    SENYERA("Senyera", 0xFFFCDD09.toInt(), 0xFFE9C600.toInt(), 0xFFDA121A.toInt(), 0xFF2B1B00.toInt(), 0xFFDA121A.toInt()),
    PAPER("Paper", 0xFFFFF8F0.toInt(), 0xFFE7D8C6.toInt(), 0xFF9A4521.toInt(), 0xFF231917.toInt(), 0xFF9A4521.toInt()),
    NIT("Nit", 0xFF000000.toInt(), 0xFF2A2A2A.toInt(), 0xFFFFB59A.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFB59A.toInt()),
}

data class Opcions(val format: Format, val mapa: Boolean, val comarques: Boolean, val paleta: Paleta = Paleta.TERRACOTA)

/**
 * Destinacions. Totes funcionen amb intents locals d'Android: l'app no es connecta enlloc.
 * [activitat] tria, dins de l'app, l'activitat de compartir que conté aquest text (p. ex. les
 * Stories o els missatges directes d'Instagram).
 */
enum class Desti(
    val etiqueta: String,
    val paquets: List<String>,
    val nomesImatge: Boolean = false,
    val activitat: String? = null,
) {
    INSTAGRAM("Instagram Stories", listOf("com.instagram.android"), nomesImatge = true, activitat = "stor"),
    INSTAGRAM_DIRECT("Instagram Direct", listOf("com.instagram.android"), activitat = "direct"),
    WHATSAPP("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
    MISSATGES("Missatges", listOf("com.google.android.apps.messaging", "com.samsung.android.messaging")),
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

/** Una destinació resolta al telèfon: paquet, activitat concreta i la icona real de l'app. */
data class DestiResolt(val paquet: String?, val component: ComponentName?, val icona: Drawable?)

object Compartir {

    fun text(geo: Geo, p: Progres, comarques: Boolean): String = buildString {
        val n = p.municipisVisitats.size
        append("He visitat $n dels ${milers(geo.municipis.size)} municipis dels Països Catalans (${percent(n, geo.municipis.size)})")
        if (comarques) {
            append(", ${p.comarquesVisitades.size} comarques i ${p.vegueriesVisitades.size} vegueries")
        }
        append(".\n#PaïsosCatalans")
    }

    private fun installat(ctx: Context, paquet: String): Boolean = try {
        ctx.packageManager.getPackageInfo(paquet, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Busca l'app (i l'activitat de compartir) al telèfon i en carrega la icona real. */
    fun resol(ctx: Context, d: Desti): DestiResolt {
        val pm = ctx.packageManager
        val candidats = buildList {
            if (d == Desti.MISSATGES) Telephony.Sms.getDefaultSmsPackage(ctx)?.let { add(it) }
            addAll(d.paquets)
        }.distinct()
        val paquet = candidats.firstOrNull { installat(ctx, it) } ?: return DestiResolt(null, null, null)
        val activitats = pm.queryIntentActivities(
            Intent(Intent.ACTION_SEND).setType("image/png").setPackage(paquet), 0,
        ) + pm.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(paquet), 0)
        val triada = d.activitat?.let { clau ->
            activitats.firstOrNull { it.activityInfo.name.contains(clau, ignoreCase = true) }
        } ?: activitats.firstOrNull()
        val component = triada?.let { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        val icona = try {
            if (d.activitat != null && triada != null) triada.loadIcon(pm) else pm.getApplicationIcon(paquet)
        } catch (_: Exception) {
            null
        }
        return DestiResolt(paquet, component?.takeIf { d.activitat != null }, icona)
    }

    fun renderitza(geo: Geo, model: ModelMapa, p: Progres, nom: String, article: String, o: Opcions): Bitmap? {
        if (o.format == Format.TEXT) return null
        val w = o.format.amplada
        val h = o.format.alcada
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pal = o.paleta
        c.drawColor(pal.fons)
        val negre = Typeface.create(Typeface.DEFAULT, 900, false)
        val semi = Typeface.create(Typeface.DEFAULT, 600, false)
        fun paint(mida: Float, color: Int, tf: Typeface, espai: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = mida; this.color = color; typeface = tf; letterSpacing = espai
        }
        val n = p.municipisVisitats.size
        val capcalera = if (nom.isNotBlank()) "EL MAPA ${Article.genitiu(nom, article).uppercase()}" else "PAÏSOS CATALANS"

        if (o.format == Format.HISTORIA) {
            c.drawText(capcalera, 96f, 170f, paint(42f, pal.text, semi, 0.12f))
            if (o.mapa) dibuixaMapa(c, model, p, pal, Rect(80f, 240f, w - 80f, 1260f))
            c.drawText(milers(n), 90f, 1530f, paint(260f, pal.accent, negre, -0.03f))
            c.drawText("municipis visitats · ${percent(n, geo.municipis.size)}", 96f, 1610f, paint(52f, pal.text, semi))
            if (o.comarques) {
                c.drawText("${p.comarquesVisitades.size} comarques · ${p.vegueriesVisitades.size} vegueries",
                    96f, 1690f, paint(44f, pal.text, semi))
            }
            c.drawText("#PaïsosCatalans", 96f, 1830f, paint(40f, pal.accent, semi))
        } else {
            c.drawText(capcalera, 72f, 120f, paint(34f, pal.text, semi, 0.12f))
            val esquerra = if (o.mapa) 470f else w - 72f
            c.drawText(milers(n), 64f, 600f, paint(if (o.mapa) 190f else 260f, pal.accent, negre, -0.03f))
            c.drawText("municipis · ${percent(n, geo.municipis.size)}", 72f, 680f, paint(44f, pal.text, semi))
            if (o.comarques) {
                c.drawText("${p.comarquesVisitades.size} comarques", 72f, 750f, paint(38f, pal.text, semi))
                c.drawText("${p.vegueriesVisitades.size} vegueries", 72f, 805f, paint(38f, pal.text, semi))
            }
            c.drawText("#PaïsosCatalans", 72f, 990f, paint(34f, pal.accent, semi))
            if (o.mapa) dibuixaMapa(c, model, p, pal, Rect(esquerra, 160f, w - 40f, h - 60f))
        }
        return bmp
    }

    private fun dibuixaMapa(c: Canvas, model: ModelMapa, p: Progres, pal: Paleta, area: Rect) {
        val r = model.limits
        val k = min(area.width / r.width, area.height / r.height)
        val fons = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.mapa; style = Paint.Style.FILL }
        val vist = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.visitat; style = Paint.Style.FILL }
        val vora = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.fons; style = Paint.Style.STROKE; strokeWidth = 1.2f / k
        }
        c.save()
        c.translate(area.center.x - r.center.x * k, area.center.y - r.center.y * k)
        c.scale(k, k)
        for (e in model.municipis) c.drawPath(e.path.asAndroidPath(), if (e.id in p.municipisVisitats) vist else fons)
        for (e in model.comarques) c.drawPath(e.path.asAndroidPath(), vora)
        model.requadreAlguer?.let {
            c.drawRoundRect(it.left, it.top, it.right, it.bottom, it.width * 0.18f, it.width * 0.18f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent; style = Paint.Style.STROKE; strokeWidth = 2f / k })
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
        val r = d?.let { resol(ctx, it) }

        if (d?.nomesImatge == true && uri == null) return "${d.etiqueta} necessita una imatge: tria Història o Publicació"

        if (d != null && r?.paquet == null) {
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
            when {
                r?.component != null -> component = r.component
                r?.paquet != null -> setPackage(r.paquet)
            }
        }
        return try {
            if (r?.paquet != null) ctx.startActivity(intent)
            else ctx.startActivity(Intent.createChooser(intent, d?.etiqueta ?: "Comparteix"))
            null
        } catch (_: Exception) {
            // Si l'activitat concreta falla, prova amb el paquet sencer
            try {
                intent.component = null
                r?.paquet?.let { intent.setPackage(it) }
                ctx.startActivity(intent)
                null
            } catch (e: Exception) {
                "No s'ha pogut obrir: ${e.localizedMessage}"
            }
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
