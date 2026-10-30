package cat.paisoscatalans.ui.mapa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/** Estat de la càmera del mapa: escala (px per unitat de món) i desplaçament (px). */
@Stable
class Camera {
    var escala by mutableFloatStateOf(1f)
    var origen by mutableStateOf(Offset.Zero)
    var mida by mutableStateOf(IntSize.Zero)
    private var escalaBase = 1f
    var inicialitzada = false
        private set
    val zoomRelatiu: Float get() = escala / escalaBase

    fun encaixa(r: Rect, marge: Float = 0.06f) {
        if (mida.width == 0 || mida.height == 0 || r.width <= 0f || r.height <= 0f) return
        val w = mida.width * (1 - 2 * marge)
        val h = mida.height * (1 - 2 * marge)
        escala = min(w / r.width, h / r.height)
        origen = Offset(
            mida.width / 2f - r.center.x * escala,
            mida.height / 2f - r.center.y * escala,
        )
        if (!inicialitzada) escalaBase = escala
        inicialitzada = true
    }

    fun transforma(centre: Offset, pan: Offset, zoom: Float) {
        val nova = (escala * zoom).coerceIn(escalaBase * 0.7f, escalaBase * 60f)
        val k = nova / escala
        origen = (origen - centre) * k + centre + pan
        escala = nova
    }

    fun aMon(p: Offset): Offset = (p - origen) / escala

    fun centra(p: Offset, zoomMinim: Float = 6f) {
        if (escala < escalaBase * zoomMinim) escala = escalaBase * zoomMinim
        origen = Offset(mida.width / 2f - p.x * escala, mida.height / 2f - p.y * escala)
    }
}

@Composable
fun rememberCamera() = remember { Camera() }

/**
 * Mapa vectorial fet a mà amb Canvas: sense tessel·les ni connexió.
 * @param colorDe color d'emplenament per a cada element de la capa activa.
 */
@Composable
fun MapaVectorial(
    model: ModelMapa,
    capa: Capa,
    camera: Camera,
    colorDe: (Element) -> Color,
    seleccionat: String?,
    onToc: (Element?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val buit = cs.surfaceContainerHighest
    val vora = cs.surface
    val voraTerritori = cs.outline
    val seleccio = cs.onSurface
    val tocActual by rememberUpdatedState(onToc)

    Canvas(
        modifier
            .onSizeChanged {
                camera.mida = it
                if (!camera.inicialitzada) camera.encaixa(model.limits)
            }
            .pointerInput(model) {
                detectTransformGestures { centre, pan, zoom, _ -> camera.transforma(centre, pan, zoom) }
            }
            .pointerInput(model, capa) {
                detectTapGestures { pos -> tocActual(model.toca(capa, camera.aMon(pos))) }
            },
    ) {
        val px = 1f / camera.escala
        withTransform({
            translate(camera.origen.x, camera.origen.y)
            scale(camera.escala, camera.escala, pivot = Offset.Zero)
        }) {
            model.requadreAlguer?.let { r ->
                drawRoundRect(
                    color = cs.surfaceContainerLow,
                    topLeft = r.topLeft, size = r.size,
                    cornerRadius = CornerRadius(r.width * 0.18f),
                )
                drawRoundRect(
                    color = voraTerritori,
                    topLeft = r.topLeft, size = r.size,
                    cornerRadius = CornerRadius(r.width * 0.18f),
                    style = Stroke(width = px),
                )
            }
            when (capa) {
                Capa.MUNICIPIS -> {
                    for (e in model.municipis) drawPath(e.path, colorDe(e))
                    // Les vores de municipi només quan s'hi ha fet zoom (si no, és soroll)
                    if (camera.zoomRelatiu > 3f) for (e in model.municipis) drawPath(e.path, vora, style = Stroke(0.6f * px))
                    for (e in model.comarques) drawPath(e.path, vora, style = Stroke(1.1f * px))
                }
                Capa.COMARQUES -> {
                    for (e in model.comarques) {
                        drawPath(e.path, colorDe(e))
                        drawPath(e.path, vora, style = Stroke(1.1f * px))
                    }
                }
                Capa.VEGUERIES -> {
                    for (e in model.territoris) if (e.id != "PR") drawPath(e.path, buit.copy(alpha = 0.55f))
                    for (e in model.vegueries) {
                        drawPath(e.path, colorDe(e))
                        drawPath(e.path, vora, style = Stroke(1.6f * px))
                    }
                }
            }
            for (e in model.territoris) drawPath(e.path, voraTerritori, style = Stroke(1.4f * px))
            if (seleccionat != null) {
                model.elements(capa).firstOrNull { it.id == seleccionat }?.let {
                    drawPath(it.path, seleccio, style = Stroke(3f * px))
                }
            }
        }
    }
}

/** Dibuixa un o més elements encaixats dins la caixa (silueta del joc, comarca, etc.). */
@Composable
fun Silueta(
    elements: List<Pair<Element, Color>>,
    modifier: Modifier = Modifier,
    vora: Color? = null,
    marge: Float = 0.08f,
) {
    Canvas(modifier) { dibuixaEncaixat(elements, vora, marge) }
}

fun DrawScope.dibuixaEncaixat(elements: List<Pair<Element, Color>>, vora: Color?, marge: Float) {
    if (elements.isEmpty()) return
    val r = elements.map { it.first.limits }.reduce { a, b ->
        Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }
    if (r.width <= 0f || r.height <= 0f) return
    val disp = Size(size.width * (1 - 2 * marge), size.height * (1 - 2 * marge))
    val k = min(disp.width / r.width, disp.height / r.height)
    translate(size.width / 2f - r.center.x * k, size.height / 2f - r.center.y * k) {
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            for ((e, c) in elements) drawPath(e.path, c)
            if (vora != null) for ((e, _) in elements) drawPath(e.path, vora, style = Stroke(1.2f / k))
        }
    }
}
