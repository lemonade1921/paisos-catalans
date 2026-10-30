package cat.paisoscatalans.ui.benvinguda

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Start
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cat.paisoscatalans.ui.SelectorArticle
import kotlinx.coroutines.launch

private data class Funcio(val icona: ImageVector, val titol: String, val text: String)

private val funcions = listOf(
    Funcio(Icons.Filled.Map, "Mapa", "Marca els municipis que has visitat i mira com s'omplen les comarques i les vegueries."),
    Funcio(Icons.Filled.Insights, "Progrés", "Segueix el teu avenç per territori i per comarca, de Salses a Guardamar i de Fraga a Maó."),
    Funcio(Icons.Filled.Casino, "Joc diari", "Endevina cada dia el municipi amagat amb sis intents. No perdis la ratxa!"),
    Funcio(Icons.Filled.MilitaryTech, "Insígnies", "Guanya medalles visitant municipis, comarques, vegueries i territoris."),
    Funcio(Icons.Filled.Share, "Comparteix", "Envia el teu mapa a Instagram Stories, Signal, Molly, Mastodon o Bluesky."),
    Funcio(Icons.Filled.Lock, "Privadesa", "Funciona sense connexió. Les teves dades només es desen en aquest telèfon."),
)

/**
 * Primera pantalla de l'app: presenta les funcions, demana el nom i l'article
 * i pregunta si es vol restaurar una còpia de seguretat o començar de zero.
 */
@Composable
fun PantallaBenvinguda(
    onComencaDeZero: (nom: String, article: String) -> Unit,
    onRestaura: suspend (Uri) -> String?,
) {
    val cs = MaterialTheme.colorScheme
    var pas by rememberSaveable { mutableIntStateOf(0) }
    var nom by rememberSaveable { mutableStateOf("") }
    var article by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var restaurant by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val obre = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restaurant = true
            scope.launch {
                error = onRestaura(uri)
                restaurant = false
            }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        // Indicador de pas
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (i == pas) 28.dp else 10.dp, height = 10.dp)
                        .background(if (i == pas) cs.primary else cs.outlineVariant, CircleShape),
                )
            }
        }

        AnimatedContent(
            targetState = pas,
            transitionSpec = {
                val endavant = targetState > initialState
                slideInHorizontally { if (endavant) it else -it } togetherWith
                    slideOutHorizontally { if (endavant) -it else it }
            },
            label = "benvinguda",
            modifier = Modifier.weight(1f),
        ) { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (p) {
                    0 -> {
                        Box(
                            Modifier.size(112.dp).background(cs.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Map, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(52.dp)) }
                        Text("Et donem la benvinguda als Països Catalans", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Una app per recórrer els territoris de parla catalana, municipi a municipi.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant,
                        )
                        funcions.forEach { f ->
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier.size(44.dp).background(cs.secondaryContainer, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(f.icona, contentDescription = null, tint = cs.onSecondaryContainer) }
                                Column(Modifier.padding(start = 14.dp)) {
                                    Text(f.titol, style = MaterialTheme.typography.titleMedium)
                                    Text(f.text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    1 -> {
                        Text("Com et dius?", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "El nom surt al teu perfil i a les imatges que comparteixis. Pots deixar-lo en blanc.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = nom,
                            onValueChange = { nom = it.take(40) },
                            label = { Text("El teu nom") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SelectorArticle(nom, article, onTria = { article = it })
                    }
                    else -> {
                        Text("Tens una còpia de seguretat?", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Si ja havies fet servir l'app, pots recuperar els llocs visitats, les insígnies, " +
                                "les ratxes del joc i els ajustos des d'un fitxer de còpia.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant,
                        )
                        Opcio(
                            Icons.Filled.RestorePage,
                            "Restaura una còpia",
                            "Tria el fitxer .json que vas desar des d'Ajustos.",
                            destacada = false,
                        ) { obre.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                        Opcio(
                            Icons.Filled.Start,
                            "Comença de zero",
                            "Un mapa en blanc per començar a explorar.",
                            destacada = true,
                        ) { onComencaDeZero(nom, article) }
                        if (restaurant) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LoadingIndicator(Modifier.size(40.dp))
                                Text("  Restaurant la còpia…")
                            }
                        }
                        error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pas > 0) TextButton(onClick = { pas--; error = null }) { Text("Enrere") }
            Spacer(Modifier.weight(1f))
            if (pas < 2) {
                Button(onClick = { pas++ }, modifier = Modifier.height(56.dp)) {
                    Text(if (pas == 0) "Comença" else "Continua")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun Opcio(icona: ImageVector, titol: String, text: String, destacada: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (destacada) cs.primary else cs.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icona, contentDescription = null, tint = if (destacada) cs.onPrimary else cs.primary,
                modifier = Modifier.size(32.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(titol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = if (destacada) cs.onPrimary else cs.onSurface)
                Text(text, style = MaterialTheme.typography.bodyMedium,
                    color = if (destacada) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
    }
}
