package cat.paisoscatalans.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Resultat del joc d'un dia. `intents` són identificadors de municipi. */
data class Partida(
    val intents: List<String> = emptyList(),
    val encertada: Boolean = false,
    val acabada: Boolean = false,
    val pistes: Int = 0,
)

/** Tot el que l'usuari genera. Es desa en un fitxer JSON privat de l'app. */
data class Estat(
    val nom: String = "",
    /** Aparença: "sistema", "clar", "fosc" o "oled" */
    val tema: String = "sistema",
    val teFoto: Boolean = false,
    val fotoVersio: Long = 0,
    /** id de municipi -> dia (epochDay) en què es va marcar com a visitat */
    val visitats: Map<String, Long> = emptyMap(),
    /** dia (epochDay) -> partida */
    val partides: Map<Long, Partida> = emptyMap(),
)

/**
 * Emmagatzematge local. Res no surt del dispositiu: el fitxer viu a
 * l'emmagatzematge intern de l'app i les còpies de seguretat estan desactivades.
 */
class Magatzem(private val context: Context) {
    private val fitxer = File(context.filesDir, "estat.json")
    val fitxerFoto = File(context.filesDir, "perfil.jpg")
    private val mutex = Mutex()
    private val _estat = MutableStateFlow(Estat())
    val estat: StateFlow<Estat> = _estat.asStateFlow()

    suspend fun carrega() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _estat.value = try {
                if (fitxer.exists()) llegeix(JSONObject(fitxer.readText())) else Estat()
            } catch (_: Exception) {
                Estat()
            }.copy(teFoto = fitxerFoto.exists())
        }
    }

    suspend fun actualitza(canvi: (Estat) -> Estat) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val nou = canvi(_estat.value)
            _estat.value = nou
            val tmp = File(fitxer.parentFile, "estat.json.tmp")
            tmp.writeText(escriu(nou).toString())
            if (!tmp.renameTo(fitxer)) {
                fitxer.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    suspend fun esborraTot() = withContext(Dispatchers.IO) {
        mutex.withLock {
            fitxer.delete()
            fitxerFoto.delete()
            // L'aparença és una preferència de l'app, no una dada personal: la conservem.
            val tema = _estat.value.tema
            _estat.value = Estat(tema = tema)
            if (tema != "sistema") fitxer.writeText(escriu(_estat.value).toString())
        }
    }

    private fun llegeix(o: JSONObject): Estat {
        val vis = mutableMapOf<String, Long>()
        o.optJSONObject("visitats")?.let { v -> v.keys().forEach { vis[it] = v.getLong(it) } }
        val parts = mutableMapOf<Long, Partida>()
        o.optJSONObject("partides")?.let { p ->
            p.keys().forEach { k ->
                val j = p.getJSONObject(k)
                val ints = j.optJSONArray("intents") ?: JSONArray()
                parts[k.toLong()] = Partida(
                    intents = (0 until ints.length()).map { ints.getString(it) },
                    encertada = j.optBoolean("encertada"),
                    acabada = j.optBoolean("acabada"),
                    pistes = j.optInt("pistes"),
                )
            }
        }
        return Estat(
            nom = o.optString("nom"),
            tema = o.optString("tema", "sistema"),
            fotoVersio = o.optLong("fotoVersio"),
            visitats = vis,
            partides = parts,
        )
    }

    private fun escriu(e: Estat): JSONObject = JSONObject().apply {
        put("versio", 1)
        put("nom", e.nom)
        put("tema", e.tema)
        put("fotoVersio", e.fotoVersio)
        put("visitats", JSONObject().apply { e.visitats.forEach { (k, v) -> put(k, v) } })
        put("partides", JSONObject().apply {
            e.partides.forEach { (dia, p) ->
                put(dia.toString(), JSONObject().apply {
                    put("intents", JSONArray(p.intents))
                    put("encertada", p.encertada)
                    put("acabada", p.acabada)
                    put("pistes", p.pistes)
                })
            }
        })
    }
}
