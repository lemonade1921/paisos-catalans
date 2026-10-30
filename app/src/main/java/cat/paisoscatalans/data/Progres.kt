package cat.paisoscatalans.data

/** Recompte de visites derivat de l'estat i de la geografia. */
class Progres(geo: Geo, estat: Estat) {
    val municipisVisitats: Set<String> = estat.visitats.keys.filter { it in geo.municipi }.toSet()

    /** comarca -> nombre de municipis visitats */
    val perComarca: Map<String, Int> =
        municipisVisitats.groupingBy { geo.municipi.getValue(it).comarca }.eachCount()

    val comarquesVisitades: Set<String> = perComarca.keys
    val comarquesCompletes: Set<String> = perComarca.filter { (c, n) ->
        n >= (geo.municipisDeComarca[c]?.size ?: Int.MAX_VALUE)
    }.keys

    val vegueriesVisitades: Set<String> =
        comarquesVisitades.mapNotNull { geo.comarca[it]?.vegueria }.toSet()
    val vegueriesCompletes: Set<String> = geo.comarquesDeVegueria.filter { (_, cs) ->
        cs.all { it.id in comarquesCompletes }
    }.keys

    val territorisVisitats: Set<String> =
        comarquesVisitades.mapNotNull { geo.comarca[it]?.territori }.toSet()

    /** territori -> municipis visitats */
    val perTerritori: Map<String, Int> =
        municipisVisitats.groupingBy { geo.territoriDe(geo.municipi.getValue(it)).id }.eachCount()

    fun fraccioComarca(geo: Geo, id: String): Float {
        val total = geo.municipisDeComarca[id]?.size ?: return 0f
        return (perComarca[id] ?: 0) / total.toFloat()
    }

    fun fraccioVegueria(geo: Geo, id: String): Float {
        val ms = geo.comarquesDeVegueria[id]?.sumOf { geo.municipisDeComarca[it.id]?.size ?: 0 } ?: return 0f
        if (ms == 0) return 0f
        val v = geo.comarquesDeVegueria[id]!!.sumOf { perComarca[it.id] ?: 0 }
        return v / ms.toFloat()
    }
}

enum class Categoria(val titol: String) {
    MUNICIPIS("Municipis"),
    COMARQUES("Comarques"),
    VEGUERIES("Vegueries"),
    TERRITORIS("Territoris"),
    RATXA("Ratxes del joc"),
}

data class Insignia(
    val id: String,
    val titol: String,
    val descripcio: String,
    val categoria: Categoria,
    val objectiu: Int,
    val progres: Int,
) {
    val aconseguida: Boolean get() = progres >= objectiu
}

object Insignies {
    fun calcula(geo: Geo, p: Progres, ratxaMaxima: Int): List<Insignia> {
        val out = mutableListOf<Insignia>()
        val nm = p.municipisVisitats.size
        val totalM = geo.municipis.size
        val llindarsM = listOf(
            1 to "Primera passa", 10 to "Explorador local", 50 to "Rodamón", 100 to "Centenari",
            250 to "Viatger incansable", 500 to "Mig miler", 1000 to "Mil municipis",
        )
        llindarsM.filter { it.first < totalM }.forEach { (n, t) ->
            out += Insignia("m$n", t, if (n == 1) "Visita el teu primer municipi" else "Visita $n municipis",
                Categoria.MUNICIPIS, n, nm)
        }
        out += Insignia("m-tots", "Tots els municipis", "Visita els $totalM municipis", Categoria.MUNICIPIS, totalM, nm)

        val nc = p.comarquesVisitades.size
        val totalC = geo.comarques.size
        listOf(1 to "Primera comarca", 5 to "Cinc comarques", 10 to "Deu comarques",
            25 to "Vint-i-cinc comarques", 50 to "Cinquanta comarques").filter { it.first < totalC }
            .forEach { (n, t) ->
                out += Insignia("c$n", t, if (n == 1) "Visita un municipi d'una comarca" else "Visita $n comarques diferents",
                    Categoria.COMARQUES, n, nc)
            }
        out += Insignia("c-totes", "Totes les comarques", "Visita les $totalC comarques", Categoria.COMARQUES, totalC, nc)
        val ncc = p.comarquesCompletes.size
        out += Insignia("cc1", "Comarca completa", "Visita tots els municipis d'una comarca", Categoria.COMARQUES, 1, ncc)
        out += Insignia("cc5", "Cinc comarques completes", "Completa 5 comarques", Categoria.COMARQUES, 5, ncc)

        val nv = p.vegueriesVisitades.size
        val totalV = geo.vegueries.size
        if (totalV > 0) {
            out += Insignia("v1", "Primera vegueria", "Visita una vegueria del Principat", Categoria.VEGUERIES, 1, nv)
            if (totalV > 4) out += Insignia("v4", "Quatre vegueries", "Visita 4 vegueries", Categoria.VEGUERIES, 4, nv)
            out += Insignia("v-totes", "Totes les vegueries", "Visita les $totalV vegueries", Categoria.VEGUERIES, totalV, nv)
            out += Insignia("vc1", "Vegueria completa", "Visita tots els municipis d'una vegueria",
                Categoria.VEGUERIES, 1, p.vegueriesCompletes.size)
        }

        geo.territoris.forEach { t ->
            out += Insignia("t-${t.id}", t.nom, "Visita algun municipi de: ${t.nom}", Categoria.TERRITORIS, 1,
                if (t.id in p.territorisVisitats) 1 else 0)
        }
        out += Insignia("t-tots", "De Salses a Guardamar", "Visita tots els territoris dels Països Catalans",
            Categoria.TERRITORIS, geo.territoris.size, p.territorisVisitats.size)

        listOf(3 to "Tres dies seguits", 7 to "Una setmana", 14 to "Dues setmanes", 30 to "Un mes sencer",
            50 to "Cinquanta dies", 100 to "Cent dies", 365 to "Un any sense parar").forEach { (n, t) ->
            out += Insignia("r$n", t, "Juga el joc diari $n dies seguits", Categoria.RATXA, n, ratxaMaxima)
        }
        return out
    }
}
