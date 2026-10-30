package cat.paisoscatalans.ui.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.Poly
import kotlin.math.cos

enum class Capa(val etiqueta: String) {
    MUNICIPIS("Municipis"),
    COMARQUES("Comarques"),
    VEGUERIES("Vegueries"),
}

/** Una forma ja projectada a coordenades "de món" (x = lon·cos φ₀, y = −lat). */
class Element(val id: String, val territori: String, anells: List<FloatArray>) {
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    val limits: Rect
    private val anellsMon: List<FloatArray> = anells

    init {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (a in anells) {
            if (a.size < 6) continue
            path.moveTo(a[0], a[1])
            var i = 2
            while (i < a.size) { path.lineTo(a[i], a[i + 1]); i += 2 }
            path.close()
            i = 0
            while (i < a.size) {
                if (a[i] < l) l = a[i]
                if (a[i] > r) r = a[i]
                if (a[i + 1] < t) t = a[i + 1]
                if (a[i + 1] > b) b = a[i + 1]
                i += 2
            }
        }
        limits = if (l <= r) Rect(l, t, r, b) else Rect.Zero
    }

    val centre: Offset get() = limits.center

    fun conte(p: Offset): Boolean {
        if (!limits.contains(p)) return false
        var dins = false
        for (a in anellsMon) {
            val n = a.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = a[2 * i]; val yi = a[2 * i + 1]
                val xj = a[2 * j]; val yj = a[2 * j + 1]
                if ((yi > p.y) != (yj > p.y) && p.x < (xj - xi) * (p.y - yi) / (yj - yi) + xi) dins = !dins
                j = i
            }
        }
        return dins
    }
}

/**
 * Totes les formes preparades per dibuixar. L'Alguer es desplaça cap a l'oest perquè
 * aparegui en un requadre al costat de les Balears (la seva posició real és a Sardenya).
 */
class ModelMapa(val geo: Geo) {
    companion object {
        val K = cos(Math.toRadians(40.5)).toFloat()
        const val DESPLACAMENT_ALGUER = -3.6f
    }

    private fun projecta(p: Poly, territori: String): List<FloatArray> {
        val dx = if (territori == "AL") DESPLACAMENT_ALGUER else 0f
        return p.anells.map { r ->
            FloatArray(r.size) { i -> if (i % 2 == 0) (r[i] + dx) * K else -r[i] }
        }
    }

    val territoris = geo.territoris.map { Element(it.id, it.id, projecta(it.forma, it.id)) }
    val vegueries = geo.vegueries.map { Element(it.id, it.territori, projecta(it.forma, it.territori)) }
    val comarques = geo.comarques.map { Element(it.id, it.territori, projecta(it.forma, it.territori)) }
    val municipis = geo.municipis.map {
        val t = geo.comarca.getValue(it.comarca).territori
        Element(it.id, t, projecta(it.forma, t))
    }
    val municipi = municipis.associateBy { it.id }
    val comarca = comarques.associateBy { it.id }

    val limits: Rect = territoris.map { it.limits }.reduceOrNull { a, b ->
        Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    } ?: Rect(0f, 0f, 1f, 1f)

    val requadreAlguer: Rect? = territoris.firstOrNull { it.id == "AL" }?.limits?.let {
        val m = 0.12f
        Rect(it.left - m, it.top - m, it.right + m, it.bottom + m)
    }

    fun elements(capa: Capa): List<Element> = when (capa) {
        Capa.MUNICIPIS -> municipis
        Capa.COMARQUES -> comarques
        Capa.VEGUERIES -> vegueries
    }

    fun toca(capa: Capa, p: Offset): Element? = elements(capa).firstOrNull { it.conte(p) }
}
