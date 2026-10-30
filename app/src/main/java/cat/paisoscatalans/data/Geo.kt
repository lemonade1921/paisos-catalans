package cat.paisoscatalans.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.text.Normalizer

/** Un polígon (o diversos) en graus: anells amb longituds (xs) i latituds (ys). */
class Poly(val anells: List<FloatArray>) {
    // Cada anell: [lon0, lat0, lon1, lat1, ...]
    val minLon: Float
    val minLat: Float
    val maxLon: Float
    val maxLat: Float

    init {
        var a = Float.MAX_VALUE
        var b = Float.MAX_VALUE
        var c = -Float.MAX_VALUE
        var d = -Float.MAX_VALUE
        for (r in anells) {
            var i = 0
            while (i < r.size) {
                if (r[i] < a) a = r[i]
                if (r[i] > c) c = r[i]
                if (r[i + 1] < b) b = r[i + 1]
                if (r[i + 1] > d) d = r[i + 1]
                i += 2
            }
        }
        minLon = a; minLat = b; maxLon = c; maxLat = d
    }

    /** Regla parell-senar sobre tots els anells (els forats queden fora). */
    fun conte(lon: Float, lat: Float): Boolean {
        if (lon < minLon || lon > maxLon || lat < minLat || lat > maxLat) return false
        var dins = false
        for (r in anells) {
            val n = r.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = r[2 * i]; val yi = r[2 * i + 1]
                val xj = r[2 * j]; val yj = r[2 * j + 1]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) dins = !dins
                j = i
            }
        }
        return dins
    }
}

data class Territori(val id: String, val nom: String, val forma: Poly)
data class Vegueria(val id: String, val nom: String, val territori: String, val forma: Poly) {
    /** "Vegueria de l'Alt Pirineu", "Vegueria del Penedès", "Vegueria de les Terres de l'Ebre"… */
    val nomComplet: String get() = "Vegueria " + deVegueria(nom)
}

/** Preposició "de" amb l'article que porta cada vegueria. */
fun deVegueria(nom: String): String = when (nom) {
    "Alt Pirineu", "Alt Pirineu i Aran" -> "de l'$nom"
    "Penedès", "Camp de Tarragona" -> "del $nom"
    "Terres de l'Ebre" -> "de les $nom"
    "Catalunya Central" -> "de la $nom"
    else -> if (nom.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "d'$nom" else "de $nom"
}
data class Comarca(val id: String, val nom: String, val territori: String, val vegueria: String?, val forma: Poly)
data class Municipi(
    val id: String,
    val nom: String,
    val comarca: String,
    val lat: Double,
    val lon: Double,
    val forma: Poly,
) {
    val clauCerca: String = normalitza(nom)
}

/** Totes les dades geogràfiques, carregades una sola vegada des dels recursos de l'app. */
class Geo(
    val versio: String,
    val atribucio: String,
    val territoris: List<Territori>,
    val vegueries: List<Vegueria>,
    val comarques: List<Comarca>,
    val municipis: List<Municipi>,
) {
    val territori = territoris.associateBy { it.id }
    val vegueria = vegueries.associateBy { it.id }
    val comarca = comarques.associateBy { it.id }
    val municipi = municipis.associateBy { it.id }
    val municipisDeComarca: Map<String, List<Municipi>> = municipis.groupBy { it.comarca }
    val comarquesDeVegueria: Map<String, List<Comarca>> =
        comarques.filter { it.vegueria != null }.groupBy { it.vegueria!! }
    val comarquesDeTerritori: Map<String, List<Comarca>> = comarques.groupBy { it.territori }
    val municipisDeTerritori: Map<String, List<Municipi>> =
        municipis.groupBy { comarca.getValue(it.comarca).territori }

    fun territoriDe(m: Municipi): Territori = territori.getValue(comarca.getValue(m.comarca).territori)
    fun comarcaDe(m: Municipi): Comarca = comarca.getValue(m.comarca)
    fun vegueriaDe(m: Municipi): Vegueria? = comarca.getValue(m.comarca).vegueria?.let { vegueria[it] }

    fun cerca(text: String, limit: Int = 8): List<Municipi> {
        val q = normalitza(text)
        if (q.isBlank()) return emptyList()
        val comencen = municipis.filter { it.clauCerca.startsWith(q) }
        val contenen = municipis.filter { !it.clauCerca.startsWith(q) && it.clauCerca.contains(q) }
        return (comencen.sortedBy { it.nom.length } + contenen).take(limit)
    }

    companion object {
        const val FITXER = "geo.json"

        /** Retorna null si l'APK s'ha compilat sense dades (vegeu tools/build_data.py). */
        fun carrega(context: Context): Geo? {
            val text = try {
                context.assets.open(FITXER).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: FileNotFoundException) {
                return null
            }
            return parse(JSONObject(text))
        }

        private fun anells(arr: JSONArray?): Poly {
            if (arr == null) return Poly(emptyList())
            val out = ArrayList<FloatArray>(arr.length())
            for (i in 0 until arr.length()) {
                val r = arr.getJSONArray(i)
                val f = FloatArray(r.length())
                var x = 0
                var y = 0
                var k = 0
                while (k + 1 < r.length()) {
                    x += r.getInt(k)
                    y += r.getInt(k + 1)
                    f[k] = x / 10000f
                    f[k + 1] = y / 10000f
                    k += 2
                }
                out.add(f)
            }
            return Poly(out)
        }

        private fun parse(o: JSONObject): Geo {
            val ts = o.getJSONArray("territoris")
            val vs = o.getJSONArray("vegueries")
            val cs = o.getJSONArray("comarques")
            val ms = o.getJSONArray("municipis")
            return Geo(
                versio = o.optString("versio"),
                atribucio = o.optString("atribucio", "© col·laboradors d'OpenStreetMap (ODbL)"),
                territoris = (0 until ts.length()).map {
                    val t = ts.getJSONObject(it)
                    Territori(t.getString("id"), t.getString("nom"), anells(t.optJSONArray("r")))
                },
                vegueries = (0 until vs.length()).map {
                    val v = vs.getJSONObject(it)
                    Vegueria(v.getString("id"), v.getString("nom"), v.getString("t"), anells(v.optJSONArray("r")))
                },
                comarques = (0 until cs.length()).map {
                    val c = cs.getJSONObject(it)
                    Comarca(
                        c.getString("id"), c.getString("nom"), c.getString("t"),
                        if (c.isNull("v")) null else c.getString("v"),
                        anells(c.optJSONArray("r")),
                    )
                },
                municipis = (0 until ms.length()).map {
                    val m = ms.getJSONObject(it)
                    Municipi(
                        m.getString("id"), m.getString("nom"), m.getString("c"),
                        m.getDouble("lat"), m.getDouble("lon"), anells(m.optJSONArray("r")),
                    )
                },
            )
        }
    }
}

fun normalitza(s: String): String =
    Normalizer.normalize(s.lowercase().replace('’', '\''), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9' ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
