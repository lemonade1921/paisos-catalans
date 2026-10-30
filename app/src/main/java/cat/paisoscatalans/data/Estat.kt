package cat.paisoscatalans.data

import android.content.Context
import android.util.Base64
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
    /** Article personal: "", "el", "en", "la", "na", "l'" o "n'" (vegeu [Article]) */
    val article: String = "",
    /** S'ha completat la pantalla de benvinguda */
    val introduit: Boolean = false,
    /** Aparença: "sistema", "clar", "fosc" o "oled" */
    val tema: String = "sistema",
    /** Confeti quan s'aconsegueix una insígnia */
    val confeti: Boolean = true,
    /** Recordatori diari del joc i hora (minuts des de mitjanit) */
    val recordatori: Boolean = false,
    val horaRecordatori: Int = 19 * 60,
    /** Insígnies ja celebrades; null = encara no inicialitzat (no es celebra res de cop) */
    val insigniesVistes: Set<String>? = null,
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
private const val FORMAT_APP = "paisos-catalans"

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

    /**
     * Còpia de seguretat completa en JSON: perfil (amb la foto en base64), aparença,
     * llocs visitats i totes les partides del joc (d'on surten les ratxes i les insígnies).
     */
    suspend fun copiaDeSeguretat(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val o = escriu(_estat.value)
            o.put("app", FORMAT_APP)
            o.put("format", 1)
            o.put("creada", System.currentTimeMillis())
            if (fitxerFoto.exists()) {
                o.put("foto", Base64.encodeToString(fitxerFoto.readBytes(), Base64.NO_WRAP))
            }
            o.toString(2)
        }
    }

    /** Restaura una còpia. Retorna null si ha anat bé o un missatge d'error. */
    suspend fun restaura(text: String): String? = withContext(Dispatchers.IO) {
        val o = try {
            JSONObject(text)
        } catch (_: Exception) {
            return@withContext "El fitxer no és una còpia vàlida"
        }
        if (o.optString("app") != FORMAT_APP) return@withContext "Aquest fitxer no és una còpia de Països Catalans"
        mutex.withLock {
            val nou = llegeix(o).copy(introduit = true)
            val foto = o.optString("foto")
            if (foto.isNotEmpty()) {
                try {
                    fitxerFoto.writeBytes(Base64.decode(foto, Base64.DEFAULT))
                } catch (_: Exception) {
                    fitxerFoto.delete()
                }
            } else {
                fitxerFoto.delete()
            }
            val ambFoto = nou.copy(teFoto = fitxerFoto.exists(), fotoVersio = _estat.value.fotoVersio + 1)
            fitxer.writeText(escriu(ambFoto).toString())
            _estat.value = ambFoto
        }
        null
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
            article = Article.valid(o.optString("article")),
            // Un fitxer d'estat d'una versió anterior vol dir que l'app ja s'ha fet servir
            introduit = o.optBoolean("introduit", true),
            tema = o.optString("tema", "sistema"),
            confeti = o.optBoolean("confeti", true),
            recordatori = o.optBoolean("recordatori", false),
            horaRecordatori = o.optInt("horaRecordatori", 19 * 60),
            insigniesVistes = o.optJSONArray("insigniesVistes")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
            fotoVersio = o.optLong("fotoVersio"),
            visitats = vis,
            partides = parts,
        )
    }

    private fun escriu(e: Estat): JSONObject = JSONObject().apply {
        put("versio", 1)
        put("nom", e.nom)
        put("article", e.article)
        put("introduit", e.introduit)
        put("tema", e.tema)
        put("confeti", e.confeti)
        put("recordatori", e.recordatori)
        put("horaRecordatori", e.horaRecordatori)
        e.insigniesVistes?.let { put("insigniesVistes", JSONArray(it.toList())) }
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
