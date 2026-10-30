package cat.paisoscatalans

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cat.paisoscatalans.data.Article
import cat.paisoscatalans.data.Estat
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.JocDiari
import cat.paisoscatalans.data.Magatzem
import cat.paisoscatalans.data.Partida
import cat.paisoscatalans.recordatori.Recordatori
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

sealed interface Carrega {
    data object EnCurs : Carrega
    data object SenseDades : Carrega
    data class Llesta(val geo: Geo) : Carrega
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val magatzem = Magatzem(app)
    val estat: StateFlow<Estat> = magatzem.estat
    val fitxerFoto get() = magatzem.fitxerFoto

    private val _geo = MutableStateFlow<Carrega>(Carrega.EnCurs)
    val geo: StateFlow<Carrega> = _geo.asStateFlow()

    init {
        viewModelScope.launch {
            magatzem.carrega()
            magatzem.estat.value.let { e ->
                if (e.recordatori) Recordatori.programa(getApplication(), e.horaRecordatori)
            }
            val g = withContext(Dispatchers.Default) { Geo.carrega(getApplication()) }
            _geo.value = if (g == null) Carrega.SenseDades else Carrega.Llesta(g)
        }
    }

    fun commutaVisitat(id: String) = viewModelScope.launch {
        magatzem.actualitza { e ->
            val v = e.visitats.toMutableMap()
            if (v.remove(id) == null) v[id] = JocDiari.avui()
            e.copy(visitats = v)
        }
    }

    fun marcaVisitats(ids: Collection<String>) = viewModelScope.launch {
        magatzem.actualitza { e ->
            val v = e.visitats.toMutableMap()
            val avui = JocDiari.avui()
            ids.forEach { if (it !in v) v[it] = avui }
            e.copy(visitats = v)
        }
    }

    fun canviaArticle(article: String) = viewModelScope.launch {
        magatzem.actualitza { it.copy(article = Article.valid(article)) }
    }

    fun acabaBenvinguda(nom: String, article: String) = viewModelScope.launch {
        magatzem.actualitza {
            val n = nom.trim().take(40)
            it.copy(nom = n, article = Article.ajusta(n, article), introduit = true)
        }
    }

    /** Escriu la còpia de seguretat al fitxer que l'usuari ha triat. */
    suspend fun exportaCopia(uri: Uri): String? = try {
        val text = magatzem.copiaDeSeguretat()
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("no s'ha pogut obrir el fitxer")
        }
        null
    } catch (e: Exception) {
        "No s'ha pogut desar la còpia: ${e.localizedMessage}"
    }

    /** Llegeix i restaura una còpia de seguretat triada per l'usuari. */
    suspend fun importaCopia(uri: Uri): String? = try {
        val text = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("no s'ha pogut obrir el fitxer")
        }
        magatzem.restaura(text)
    } catch (e: Exception) {
        "No s'ha pogut llegir la còpia: ${e.localizedMessage}"
    }

    fun canviaConfeti(on: Boolean) = viewModelScope.launch {
        magatzem.actualitza { it.copy(confeti = on) }
    }

    /** Activa o desactiva el recordatori diari i el (re)programa. */
    fun canviaRecordatori(on: Boolean, minuts: Int) = viewModelScope.launch {
        magatzem.actualitza { it.copy(recordatori = on, horaRecordatori = minuts) }
        val ctx = getApplication<Application>()
        if (on) Recordatori.programa(ctx, minuts) else Recordatori.cancella(ctx)
    }

    fun marcaInsigniesVistes(ids: Set<String>) = viewModelScope.launch {
        magatzem.actualitza { it.copy(insigniesVistes = (it.insigniesVistes ?: emptySet()) + ids) }
    }

    fun canviaTema(tema: String) = viewModelScope.launch {
        magatzem.actualitza { it.copy(tema = tema) }
    }

    fun canviaNom(nom: String) = viewModelScope.launch {
        magatzem.actualitza { e ->
            val n = nom.trim().take(40)
            e.copy(nom = n, article = Article.ajusta(n, e.article))
        }
    }

    /** Copia la foto escollida (reduïda a 512 px) dins l'app; no en guardem l'original. */
    fun desaFoto(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val src = ImageDecoder.createSource(ctx.contentResolver, uri)
                val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                    val costat = max(info.size.width, info.size.height)
                    if (costat > 512) {
                        val k = 512f / costat
                        dec.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                    }
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                magatzem.fitxerFoto.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                true
            } catch (_: Exception) {
                false
            }
        }
        if (ok) magatzem.actualitza { it.copy(teFoto = true, fotoVersio = it.fotoVersio + 1) }
    }

    fun treuFoto() = viewModelScope.launch {
        withContext(Dispatchers.IO) { magatzem.fitxerFoto.delete() }
        magatzem.actualitza { it.copy(teFoto = false, fotoVersio = it.fotoVersio + 1) }
    }

    suspend fun llegeixFoto(): Bitmap? = withContext(Dispatchers.IO) {
        if (magatzem.fitxerFoto.exists()) BitmapFactory.decodeFile(magatzem.fitxerFoto.path) else null
    }

    fun esborraTot() = viewModelScope.launch { magatzem.esborraTot() }

    // ---- Joc diari

    fun endevina(geo: Geo, municipiId: String) = viewModelScope.launch {
        val dia = JocDiari.avui()
        val objectiu = JocDiari.municipiDelDia(geo, dia)
        magatzem.actualitza { e ->
            val p = e.partides[dia] ?: Partida()
            if (p.acabada || municipiId in p.intents) return@actualitza e
            val intents = p.intents + municipiId
            val encert = municipiId == objectiu.id
            val nova = p.copy(
                intents = intents,
                encertada = encert,
                acabada = encert || intents.size >= JocDiari.MAX_INTENTS,
            )
            e.copy(partides = e.partides + (dia to nova))
        }
    }

    fun obrePista() = viewModelScope.launch {
        val dia = JocDiari.avui()
        magatzem.actualitza { e ->
            val p = e.partides[dia] ?: Partida()
            e.copy(partides = e.partides + (dia to p.copy(pistes = p.pistes + 1)))
        }
    }
}
