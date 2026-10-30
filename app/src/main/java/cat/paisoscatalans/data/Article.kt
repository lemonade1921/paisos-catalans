package cat.paisoscatalans.data

/**
 * Article personal davant del nom: "la Maria", "en Pere", "na Joana", "el Pere",
 * "l'Arnau", "n'Àngela". L'usuari el tria al perfil o a la benvinguda.
 */
object Article {
    /** Opcions tal com es mostren (la clau desada és la versió en minúscules). */
    val opcions: List<Pair<String, String>> = listOf(
        "" to "Cap",
        "el" to "El",
        "en" to "En",
        "la" to "La",
        "na" to "Na",
        "l'" to "L'",
        "n'" to "N'",
    )

    fun valid(a: String): String = a.takeIf { c -> opcions.any { it.first == c } }.orEmpty()

    /** Comença per vocal (o h muda + vocal) i, per tant, l'article s'apostrofa. */
    fun apostrofa(nom: String): Boolean = vocal(nom)

    /**
     * Opcions que tenen sentit per a aquest nom: davant de vocal només L' i N';
     * davant de consonant només El, En, La i Na. Sempre hi ha "Cap".
     */
    fun opcionsPer(nom: String): List<Pair<String, String>> =
        if (nom.isNotBlank() && vocal(nom)) opcions.filter { it.first in setOf("", "l'", "n'") }
        else opcions.filter { it.first in setOf("", "el", "en", "la", "na") }

    /**
     * Adapta l'article quan canvia el nom: "la"/"el" → "l'" i "en"/"na" → "n'" davant de vocal;
     * "n'" → "en" davant de consonant. "l'" no indica si és el o la, i llavors queda en "Cap".
     */
    fun ajusta(nom: String, article: String): String {
        val a = valid(article)
        if (a.isEmpty() || nom.isBlank()) return a
        return if (vocal(nom)) when (a) {
            "el", "la" -> "l'"
            "en", "na" -> "n'"
            else -> a
        } else when (a) {
            "n'" -> "en"
            "l'" -> ""
            else -> a
        }
    }

    private const val VOCALS = "aeiouàáèéíïòóúüAEIOUÀÁÈÉÍÏÒÓÚÜ"

    /** Comença per vocal o per h muda + vocal ("Arnau", "Helena"). */
    private fun vocal(nom: String): Boolean {
        val n = nom.trimStart()
        if (n.isEmpty()) return false
        if (n[0] in VOCALS) return true
        return (n[0] == 'h' || n[0] == 'H') && n.length > 1 && n[1] in VOCALS
    }

    /** "la" no s'apostrofa davant d'i/u àtones ("la Irene", "la Úrsula"). */
    private fun laApostrofa(nom: String): Boolean {
        if (!vocal(nom)) return false
        val n = nom.trimStart().let { if (it[0] == 'h' || it[0] == 'H') it.drop(1) else it }
        return n[0] !in "iuIU"
    }

    /** Article + nom dins d'una frase: "la Maria", "n'Àngela", "l'Arnau", "el Pere", "Maria". */
    fun ambArticle(nom: String, article: String): String {
        val n = nom.trim()
        if (n.isEmpty()) return n
        return when (valid(article)) {
            "en", "na" -> if (vocal(n)) "n'$n" else "$article $n"
            "el" -> if (vocal(n)) "l'$n" else "el $n"
            "la" -> if (laApostrofa(n)) "l'$n" else "la $n"
            "l'" -> "l'$n"
            "n'" -> "n'$n"
            else -> n
        }
    }

    /** A l'inici d'una frase: "La Maria", "En Pere", "N'Arnau". */
    fun nominatiu(nom: String, article: String): String =
        ambArticle(nom, article).replaceFirstChar { it.uppercase() }

    /**
     * "de" + nom: "de la Maria", "d'en Pere", "del Pere", "de na Joana", "de l'Arnau",
     * "de n'Àngela", "d'Arnau", "de Maria".
     */
    fun genitiu(nom: String, article: String): String {
        val n = nom.trim()
        val a = valid(article)
        return when {
            a == "en" && !vocal(n) -> "d'en $n"
            a == "el" && !vocal(n) -> "del $n"
            a.isEmpty() -> if (vocal(n)) "d'$n" else "de $n"
            else -> "de " + ambArticle(n, a)
        }
    }
}
