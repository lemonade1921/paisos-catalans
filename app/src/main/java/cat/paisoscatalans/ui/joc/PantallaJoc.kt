package cat.paisoscatalans.ui.joc

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.paisoscatalans.data.Estat
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.JocDiari
import cat.paisoscatalans.data.Partida
import cat.paisoscatalans.data.Ratxa
import cat.paisoscatalans.ui.Xip
import cat.paisoscatalans.ui.mapa.ModelMapa
import cat.paisoscatalans.ui.mapa.Silueta
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PantallaJoc(
    geo: Geo,
    model: ModelMapa,
    estat: Estat,
    onEndevina: (String) -> Unit,
    onPista: () -> Unit,
    onMarcaVisitat: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val dia = JocDiari.avui()
    val objectiu = remember(geo, dia) { JocDiari.municipiDelDia(geo, dia) }
    val partida = estat.partides[dia] ?: Partida()
    val ratxa = Ratxa.actual(estat, dia)
    var text by rememberSaveable { mutableStateOf("") }
    val suggeriments = remember(text, geo, partida.intents) {
        geo.cerca(text, 5).filter { it.id !in partida.intents }
    }
    val data = LocalDate.ofEpochDay(dia).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ca")))

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("El municipi del dia", style = MaterialTheme.typography.headlineMedium)
                    Text("Núm. ${JocDiari.numero(dia)} · $data", style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant)
                }
                Row(
                    Modifier.background(cs.tertiaryContainer, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = cs.onTertiaryContainer,
                        modifier = Modifier.size(20.dp))
                    Text(" Ratxa $ratxa", color = cs.onTertiaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Silueta + intents + pistes
        item {
            Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(36.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(150.dp).background(cs.primaryContainer, MaterialShapes.Cookie12Sided.toShape()),
                        contentAlignment = Alignment.Center,
                    ) {
                        model.municipi[objectiu.id]?.let {
                            Silueta(
                                listOf(it to if (partida.acabada) cs.primary else cs.onPrimaryContainer),
                                Modifier.size(104.dp),
                                marge = 0.02f,
                            )
                        }
                    }
                    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Intents", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(JocDiari.MAX_INTENTS) { i ->
                                val usat = i < partida.intents.size
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .then(
                                            if (usat) Modifier.background(cs.primary, RoundedCornerShape(4.dp))
                                            else Modifier.border(2.dp, cs.outline, CircleShape)
                                        ),
                                )
                            }
                        }
                        Pistes(geo, objectiu.id, partida, onPista)
                    }
                }
            }
        }

        if (!partida.acabada) {
            item {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Escriu un municipi…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainerHigh,
                        unfocusedContainerColor = cs.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (suggeriments.isNotEmpty()) {
                item {
                    Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(20.dp)) {
                        Column {
                            suggeriments.forEachIndexed { i, m ->
                                if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onEndevina(m.id)
                                            text = ""
                                            focus.clearFocus()
                                        }
                                        .padding(horizontal = 18.dp, vertical = 12.dp),
                                ) {
                                    Text(m.nom, style = MaterialTheme.typography.titleMedium)
                                    Text(geo.territoriDe(m).nom, style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item { Resultat(geo, estat, partida, objectiu.id, dia, ratxa, onMarcaVisitat) { txt ->
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt),
                    "Comparteix el resultat",
                ))
            } }
        }

        items(partida.intents.reversed(), key = { it }) { id ->
            val m = geo.municipi[id] ?: return@items
            val encert = id == objectiu.id
            val km = JocDiari.distanciaKm(m, objectiu)
            val rumb = JocDiari.rumb(m, objectiu)
            Surface(
                color = if (encert) cs.primary else cs.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp).height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m.nom, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        color = if (encert) cs.onPrimary else cs.onSurface, maxLines = 1)
                    if (encert) {
                        Icon(Icons.Filled.Check, contentDescription = "Encertat", tint = cs.onPrimary)
                    } else {
                        Text("${km.toInt()} km", color = cs.onSurfaceVariant, modifier = Modifier.width(64.dp),
                            style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.size(32.dp).background(cs.surface, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = "Cap al ${JocDiari.nomRumb(rumb)}",
                                modifier = Modifier.size(20.dp).rotate(rumb.toFloat()),
                            )
                        }
                        Xip("${JocDiari.proximitat(km)}%", cs.tertiaryContainer, cs.onTertiaryContainer,
                            Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        item {
            Text(
                "Cada dia hi ha un municipi nou, triat al teu telèfon sense connexió. " +
                    "Tens ${JocDiari.MAX_INTENTS} intents: cada resposta et diu la distància i la direcció.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Pistes(geo: Geo, objectiuId: String, partida: Partida, onPista: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val m = geo.municipi[objectiuId] ?: return
    // Pista 1: territori (després de 2 intents). Pista 2: comarca (després de 4).
    val disponibles = when {
        partida.acabada -> 2
        partida.intents.size >= 4 -> 2
        partida.intents.size >= 2 -> 1
        else -> 0
    }
    val obertes = if (partida.acabada) 2 else partida.pistes
    if (obertes >= 1) Xip(geo.territoriDe(m).nom, cs.tertiaryContainer, cs.onTertiaryContainer)
    if (obertes >= 2) Xip("Comarca: ${geo.comarcaDe(m).nom}", cs.tertiaryContainer, cs.onTertiaryContainer)
    if (obertes < disponibles) {
        FilledTonalButton(onClick = onPista) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (obertes == 0) "  Pista: territori" else "  Pista: comarca")
        }
    } else if (obertes < 2 && !partida.acabada) {
        Text(
            if (obertes == 0) "Pista després de 2 intents" else "Una altra pista després de 4 intents",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun Resultat(
    geo: Geo,
    estat: Estat,
    partida: Partida,
    objectiuId: String,
    dia: Long,
    ratxa: Int,
    onMarcaVisitat: (String) -> Unit,
    onComparteix: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val m = geo.municipi[objectiuId] ?: return
    val n = partida.intents.size
    val text = buildString {
        append("El municipi del dia núm. ${JocDiari.numero(dia)}: ")
        append(if (partida.encertada) "encertat en $n/${JocDiari.MAX_INTENTS}" else "X/${JocDiari.MAX_INTENTS}")
        append(". Ratxa: $ratxa ${if (ratxa == 1) "dia" else "dies"}. #PaïsosCatalans")
    }
    Surface(
        color = if (partida.encertada) cs.primary else cs.inverseSurface,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val c = if (partida.encertada) cs.onPrimary else cs.inverseOnSurface
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (partida.encertada) "L'has encertat en $n ${if (n == 1) "intent" else "intents"}!" else "Demà més! Avui era…",
                color = c, style = MaterialTheme.typography.titleMedium,
            )
            Text(m.nom, color = c, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            Text("${geo.comarcaDe(m).nom} · ${geo.territoriDe(m).nom}", color = c)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(onClick = { onComparteix(text) }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  Comparteix")
                }
                if (m.id !in estat.visitats) {
                    OutlinedButton(onClick = { onMarcaVisitat(m.id) }) { Text("Hi he estat", color = c) }
                }
            }
            Text("Torna demà per a un municipi nou.", color = c, style = MaterialTheme.typography.bodySmall)
        }
    }
}
