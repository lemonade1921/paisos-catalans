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
    RESUM("Resum", 1080, 1350),
    TEXT("Text", 0, 0),
}

/** Xifres del perfil que surten al resum. */
data class Extra(val ratxa: Int = 0, val insignies: Int = 0, val totalInsignies: Int = 0)

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

data class Opcions(
    val format: Format,
    val mapa: Boolean,
    val comarques: Boolean,
    val paleta: Paleta = Paleta.TERRACOTA,
    val nom: Boolean = true,
    val percentatge: Boolean = true,
    val escuts: Boolean = false,
    val data: Boolean = false,
    val contorns: Boolean = true,
)

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

    fun renderitza(
        geo: Geo,
        model: ModelMapa,
        p: Progres,
        nom: String,
        article: String,
        o: Opcions,
        escuts: Map<String, Bitmap> = emptyMap(),
        extra: Extra = Extra(),
    ): Bitmap? {
        if (o.format == Format.TEXT) return null
        if (o.format == Format.RESUM) return resum(geo, p, nom, article, o, escuts, extra)
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
        val capcalera = if (o.nom && nom.isNotBlank()) "EL MAPA ${Article.genitiu(nom, article).uppercase()}" else "PAÏSOS CATALANS"
        val linia1 = "municipis visitats" + if (o.percentatge) " · ${percent(n, geo.municipis.size)}" else ""
        val data = java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("d MMMM 'de' yyyy", java.util.Locale.forLanguageTag("ca")),
        )
        val llistaEscuts = if (o.escuts) geo.territoris.mapNotNull { escuts[it.id] } else emptyList()

        if (o.format == Format.HISTORIA) {
            c.drawText(capcalera, 96f, 170f, paint(42f, pal.text, semi, 0.12f))
            if (o.mapa) dibuixaMapa(c, model, p, pal, o.contorns, Rect(80f, 240f, w - 80f, 1260f))
            c.drawText(milers(n), 90f, 1530f, paint(260f, pal.accent, negre, -0.03f))
            c.drawText(linia1, 96f, 1610f, paint(52f, pal.text, semi))
            if (o.comarques) {
                c.drawText("${p.comarquesVisitades.size} comarques · ${p.vegueriesVisitades.size} vegueries",
                    96f, 1690f, paint(44f, pal.text, semi))
            }
            if (llistaEscuts.isNotEmpty()) dibuixaEscuts(c, llistaEscuts, 96f, 1740f, 110f)
            if (o.data) c.drawText(data, 96f, 1880f, paint(34f, pal.text, semi).apply { alpha = 190 })
        } else {
            c.drawText(capcalera, 72f, 120f, paint(34f, pal.text, semi, 0.12f))
            val esquerra = if (o.mapa) 470f else w - 72f
            c.drawText(milers(n), 64f, 560f, paint(if (o.mapa) 190f else 260f, pal.accent, negre, -0.03f))
            c.drawText(if (o.percentatge) "municipis · ${percent(n, geo.municipis.size)}" else "municipis",
                72f, 640f, paint(44f, pal.text, semi))
            if (o.comarques) {
                c.drawText("${p.comarquesVisitades.size} comarques", 72f, 710f, paint(38f, pal.text, semi))
                c.drawText("${p.vegueriesVisitades.size} vegueries", 72f, 765f, paint(38f, pal.text, semi))
            }
            if (llistaEscuts.isNotEmpty()) dibuixaEscuts(c, llistaEscuts, 72f, 820f, if (o.mapa) 52f else 80f)
            if (o.data) c.drawText(data, 72f, 1020f, paint(30f, pal.text, semi).apply { alpha = 190 })
            if (o.mapa) dibuixaMapa(c, model, p, pal, o.contorns, Rect(esquerra, 160f, w - 40f, h - 60f))
        }
        return bmp
    }

    /**
     * Resum en rajoles, a l'estil de les diapositives de resum d'una presentació:
     * municipis amb un anell de progrés, comarques, vegueries i territoris, una barra per
     * territori amb el seu escut (en color si l'has visitat) i la ratxa i les insígnies.
     */
    private fun resum(
        geo: Geo,
        p: Progres,
        nom: String,
        article: String,
        o: Opcions,
        escuts: Map<String, Bitmap>,
        extra: Extra,
    ): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pal = o.paleta
        c.drawColor(pal.fons)
        val clar = luminancia(pal.fons) > 0.6
        val rajola = if (clar) 0x14000000 else 0x22FFFFFF
        val rajolaFort = if (clar) 0x22000000 else 0x33FFFFFF
        val negre = Typeface.create(Typeface.DEFAULT, 900, false)
        val semi = Typeface.create(Typeface.DEFAULT, 600, false)
        fun paint(mida: Float, color: Int, tf: Typeface, espai: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = mida; this.color = color; typeface = tf; letterSpacing = espai
        }
        fun caixa(l: Float, t: Float, r: Float, b: Float, color: Int = rajola) {
            c.drawRoundRect(l, t, r, b, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        fun barra(l: Float, t: Float, r: Float, alt: Float, f: Float) {
            c.drawRoundRect(l, t, r, t + alt, alt / 2, alt / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rajolaFort })
            if (f > 0f) {
                val fi = l + (r - l) * f.coerceIn(0f, 1f)
                c.drawRoundRect(l, t, maxOf(fi, l + alt), t + alt, alt / 2, alt / 2,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent })
            }
        }
        val m = 60f
        val g = 24f
        val n = p.municipisVisitats.size
        val total = geo.municipis.size
        val capcalera = if (o.nom && nom.isNotBlank()) "EL RESUM ${Article.genitiu(nom, article).uppercase()}" else "PAÏSOS CATALANS"

        // 1. Municipis amb anell
        var y = m
        caixa(m, y, w - m, y + 380f)
        c.drawText(capcalera, m + 44f, y + 80f, paint(32f, pal.text, semi, 0.12f).apply { alpha = 210 })
        c.drawText(milers(n), m + 40f, y + 250f, paint(170f, pal.accent, negre, -0.03f))
        c.drawText("de ${milers(total)} municipis", m + 44f, y + 320f, paint(44f, pal.text, semi))
        val cx = w - m - 170f
        val cy = y + 200f
        val radi = 115f
        val anell = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 34f; strokeCap = Paint.Cap.ROUND }
        c.drawCircle(cx, cy, radi, anell.apply { color = rajolaFort })
        val frac = if (total > 0) n / total.toFloat() else 0f
        if (frac > 0f) {
            c.drawArc(cx - radi, cy - radi, cx + radi, cy + radi, -90f, 360f * frac, false, anell.apply { color = pal.accent })
        }
        val pct = percent(n, total)
        val pp = paint(58f, pal.text, negre).apply { textAlign = Paint.Align.CENTER }
        c.drawText(pct, cx, cy + 20f, pp)
        y += 380f + g

        // 2. Comarques, vegueries, territoris
        val ample3 = (w - 2 * m - 2 * g) / 3f
        val dades3 = listOf(
            Triple("comarques", p.comarquesVisitades.size, geo.comarques.size),
            Triple("vegueries", p.vegueriesVisitades.size, geo.vegueries.size),
            Triple("territoris", p.territorisVisitats.size, geo.territoris.size),
        )
        dades3.forEachIndexed { i, (etiq, fet, tot) ->
            val l = m + i * (ample3 + g)
            caixa(l, y, l + ample3, y + 250f)
            c.drawText("$fet", l + 34f, y + 120f, paint(96f, pal.accent, negre, -0.02f))
            c.drawText("de $tot $etiq", l + 36f, y + 172f, paint(32f, pal.text, semi))
            barra(l + 36f, y + 200f, l + ample3 - 36f, 16f, if (tot > 0) fet / tot.toFloat() else 0f)
        }
        y += 250f + g

        // 3. Per territori, amb escuts
        val alt3 = 380f
        caixa(m, y, w - m, y + alt3)
        val filaAlt = (alt3 - 40f) / geo.territoris.size.coerceAtLeast(1)
        val gris = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
            alpha = 110
        }
        val colorP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        geo.territoris.forEachIndexed { i, t ->
            val fy = y + 20f + i * filaAlt
            val mid = fy + filaAlt / 2f
            val fet = p.perTerritori[t.id] ?: 0
            val tot = geo.municipisDeTerritori[t.id]?.size ?: 0
            val visitat = fet > 0
            val ic = filaAlt - 10f
            escuts[t.id]?.let { e ->
                val a = ic * e.width / e.height.coerceAtLeast(1)
                c.drawBitmap(e, null, android.graphics.RectF(m + 36f + (ic - a) / 2, mid - ic / 2, m + 36f + (ic + a) / 2, mid + ic / 2),
                    if (visitat) colorP else gris)
            }
            val tp = paint(28f, pal.text, semi).apply { if (!visitat) alpha = 150 }
            c.drawText(t.nom, m + 50f + ic, mid + 10f, tp)
            val bl = m + 400f
            val br = w - m - 130f
            barra(bl, mid - 8f, br, 16f, if (tot > 0) fet / tot.toFloat() else 0f)
            c.drawText(percent(fet, tot), w - m - 36f, mid + 10f, paint(28f, pal.text, semi).apply {
                textAlign = Paint.Align.RIGHT; if (!visitat) alpha = 150
            })
        }
        y += alt3 + g

        // 4. Ratxa i insígnies
        val ample2 = (w - 2 * m - g) / 2f
        val baix = h - m
        caixa(m, y, m + ample2, baix)
        c.drawText("${extra.ratxa}", m + 36f, y + 110f, paint(96f, pal.accent, negre))
        c.drawText(if (extra.ratxa == 1) "dia de ratxa" else "dies de ratxa", m + 38f, y + 160f, paint(32f, pal.text, semi))
        val l2 = m + ample2 + g
        caixa(l2, y, w - m, baix)
        c.drawText("${extra.insignies}", l2 + 36f, y + 110f, paint(96f, pal.accent, negre))
        c.drawText("de ${extra.totalInsignies} insígnies", l2 + 38f, y + 160f, paint(32f, pal.text, semi))
        if (o.data) {
            val data = java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("ca")),
            )
            c.drawText(data, w - m - 36f, baix - 30f, paint(26f, pal.text, semi).apply { textAlign = Paint.Align.RIGHT; alpha = 170 })
        }
        return bmp
    }

    private fun luminancia(color: Int): Double {
        val r = (color shr 16 and 0xFF) / 255.0
        val g = (color shr 8 and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun dibuixaEscuts(c: Canvas, escuts: List<Bitmap>, x0: Float, y0: Float, alt: Float) {
        var x = x0
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (b in escuts) {
            val ample = alt * b.width / b.height.coerceAtLeast(1)
            c.drawBitmap(b, null, android.graphics.RectF(x, y0, x + ample, y0 + alt), p)
            x += ample + alt * 0.25f
        }
    }

    private fun dibuixaMapa(c: Canvas, model: ModelMapa, p: Progres, pal: Paleta, contorns: Boolean, area: Rect) {
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
        if (contorns) for (e in model.comarques) c.drawPath(e.path.asAndroidPath(), vora)
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
