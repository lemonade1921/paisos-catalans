package cat.paisoscatalans.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import org.json.JSONObject

/** Escut d'un territori, descarregat de Wikimedia Commons en compilar (vegeu tools/escuts.py). */
class Escut(val imatge: ImageBitmap, val fitxer: String, val llicencia: String, val autor: String)

object Escuts {
    fun carrega(ctx: Context): Map<String, Escut> = try {
        val json = ctx.assets.open("escuts/escuts.json").bufferedReader().use { JSONObject(it.readText()) }
        json.keys().asSequence().mapNotNull { id ->
            val o = json.getJSONObject(id)
            val bmp = try {
                ctx.assets.open("escuts/$id.png").use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            } ?: return@mapNotNull null
            id to Escut(bmp.asImageBitmap(), o.optString("fitxer"), o.optString("llicencia"), o.optString("autor"))
        }.toMap()
    } catch (_: Exception) {
        emptyMap()
    }
}

val LocalEscuts = staticCompositionLocalOf<Map<String, Escut>> { emptyMap() }

private val grisos = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Mostra l'escut d'un territori, si n'hi ha. Retorna false si no n'hi ha cap. */
@Composable
fun EscutTerritori(id: String, mida: Dp, modifier: Modifier = Modifier, apagat: Boolean = false): Boolean {
    val e = LocalEscuts.current[id] ?: return false
    Image(
        e.imatge,
        contentDescription = "Escut",
        contentScale = ContentScale.Fit,
        colorFilter = if (apagat) grisos else null,
        alpha = if (apagat) 0.55f else 1f,
        modifier = modifier.size(mida),
    )
    return true
}
