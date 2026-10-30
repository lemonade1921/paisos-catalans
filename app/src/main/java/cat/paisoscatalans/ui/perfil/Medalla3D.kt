package cat.paisoscatalans.ui.perfil

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cat.paisoscatalans.data.Insignia
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Diàleg amb la medalla en 3D: gira sola i es pot fer girar arrossegant-la. */
@Composable
fun DialegInsignia(i: Insignia, forma: Shape, icona: ImageVector, categoria: String, onTanca: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onTanca, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = cs.surfaceContainerHigh,
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(categoria.uppercase(), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Medalla3D(i, forma, icona, Modifier.size(240.dp).padding(vertical = 8.dp))
                Text(i.titol, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(i.descripcio, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                if (i.aconseguida) {
                    Text("Aconseguida", style = MaterialTheme.typography.titleMedium, color = cs.tertiary,
                        fontWeight = FontWeight.Bold)
                } else {
                    LinearWavyProgressIndicator(
                        progress = { (i.progres.toFloat() / i.objectiu).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Text("${minOf(i.progres, i.objectiu)} de ${i.objectiu}", style = MaterialTheme.typography.titleMedium)
                }
                Text("Arrossega la medalla per fer-la girar", style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant)
                TextButton(onClick = onTanca) { Text("Tanca") }
            }
        }
    }
}

/**
 * Medalla amb aparença 3D feta amb `graphicsLayer`: cara davantera, revers, gruix
 * (capes desplaçades segons l'angle) i una lluentor que es mou en girar.
 */
@Composable
fun Medalla3D(i: Insignia, forma: Shape, icona: ImageVector, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val densitat = LocalDensity.current.density
    val guanyada = i.aconseguida

    val clar = if (guanyada) lerp(cs.tertiaryContainer, Color.White, 0.35f) else cs.surfaceBright
    val base = if (guanyada) cs.tertiaryContainer else cs.surfaceContainerHighest
    val fosc = if (guanyada) cs.tertiary else cs.outline
    val vora = lerp(fosc, Color.Black, 0.35f)
    val tinta = if (guanyada) cs.onTertiaryContainer else cs.outline

    val angle = remember { Animatable(-25f) }
    var arrossegant by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Gir automàtic suau mentre no s'arrossega
    LaunchedEffect(arrossegant) {
        if (!arrossegant) {
            var abans = withFrameNanos { it }
            while (true) {
                val ara = withFrameNanos { it }
                angle.snapTo(angle.value + (ara - abans) / 1_000_000_000f * 40f)
                abans = ara
            }
        }
    }

    Box(
        modifier
            .semantics { contentDescription = "Medalla: ${i.titol}" }
            .pointerInput(Unit) {
                val seguidor = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        arrossegant = true
                        seguidor.resetTracking()
                    },
                    onDragEnd = {
                        val v = seguidor.calculateVelocity().x
                        scope.launch {
                            angle.animateDecay(v * 0.35f, exponentialDecay(frictionMultiplier = 0.9f))
                            arrossegant = false
                        }
                    },
                    onDragCancel = { arrossegant = false },
                ) { canvi, dx ->
                    canvi.consume()
                    seguidor.addPosition(canvi.uptimeMillis, canvi.position)
                    scope.launch { angle.snapTo(angle.value + dx * 0.5f) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val a = angle.value
        val norm = ((a % 360f) + 360f) % 360f
        val davant = norm < 90f || norm > 270f
        val s = sin(Math.toRadians(a.toDouble())).toFloat()
        val signe = if (davant) -1f else 1f
        val camera = 14f * densitat

        // Gruix: capes del cantell, de la més llunyana a la més propera
        for (k in 12 downTo 1) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = a
                        cameraDistance = camera
                        translationX = signe * s * k * 1.0f * densitat
                    }
                    .background(lerp(vora, fosc, k / 14f), forma),
            )
        }

        if (davant) {
            Cara(a, camera, forma, clar, base, fosc, s) {
                Icon(icona, contentDescription = null, tint = tinta, modifier = Modifier.size(96.dp))
                if (!guanyada) {
                    Icon(
                        Icons.Filled.Lock, contentDescription = null, tint = tinta,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp).size(28.dp),
                    )
                }
            }
        } else {
            Cara(a + 180f, camera, forma, clar, base, fosc, -s) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(36.dp),
                ) {
                    Text("PAÏSOS CATALANS", style = MaterialTheme.typography.labelSmall, color = tinta,
                        fontWeight = FontWeight.Bold)
                    Text(i.titol, style = MaterialTheme.typography.titleMedium, color = tinta,
                        textAlign = TextAlign.Center, fontWeight = FontWeight.Black)
                    Text(
                        if (guanyada) "★ ★ ★" else "${minOf(i.progres, i.objectiu)}/${i.objectiu}",
                        style = MaterialTheme.typography.labelLarge, color = tinta,
                    )
                }
            }
        }
    }
}

@Composable
private fun Cara(
    rotacio: Float,
    camera: Float,
    forma: Shape,
    clar: Color,
    base: Color,
    fosc: Color,
    s: Float,
    contingut: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotacio
                cameraDistance = camera
            }
            .clip(forma)
            .background(Brush.radialGradient(listOf(clar, base, lerp(base, fosc, 0.55f))))
            .border(4.dp, lerp(fosc, base, 0.25f), forma),
        contentAlignment = Alignment.Center,
    ) {
        // Lluentor que llisca per la cara quan la medalla gira
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                        start = Offset(-400f + s * 500f, 0f),
                        end = Offset(-100f + s * 500f + 400f, 900f),
                    ),
                ),
        )
        contingut()
    }
}
