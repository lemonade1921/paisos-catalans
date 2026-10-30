package cat.paisoscatalans.ui.mapa

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.paisoscatalans.data.Estat
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.Progres
import cat.paisoscatalans.ui.Galeta
import cat.paisoscatalans.ui.GrupOpcions
import cat.paisoscatalans.ui.milers
import cat.paisoscatalans.ui.percent
import cat.paisoscatalans.ui.theme.XifraGran

@Composable
fun PantallaMapa(
    geo: Geo,
    model: ModelMapa,
    estat: Estat,
    progres: Progres,
    onCommuta: (String) -> Unit,
    onComparteix: () -> Unit,
    onObreComarca: (String) -> Unit,
    pantallaCompleta: Boolean = false,
    onPantallaCompleta: (Boolean) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val complet = pantallaCompleta
    BackHandler(enabled = complet) { onPantallaCompleta(false) }
    var capa by rememberSaveable { mutableStateOf(Capa.MUNICIPIS) }
    var seleccionat by rememberSaveable { mutableStateOf<String?>(null) }
    var cerca by rememberSaveable { mutableStateOf("") }
    val camera = rememberCamera()
    val focus = LocalFocusManager.current

    val (num, unitat, total) = when (capa) {
        Capa.MUNICIPIS -> Triple(progres.municipisVisitats.size, "municipis", geo.municipis.size)
        Capa.COMARQUES -> Triple(progres.comarquesVisitades.size, "comarques", geo.comarques.size)
        Capa.VEGUERIES -> Triple(progres.vegueriesVisitades.size, "vegueries del Principat", geo.vegueries.size)
    }

    val buit = cs.surfaceContainerHighest
    fun escala(f: Float): Color = if (f <= 0f) buit else lerp(cs.primaryContainer, cs.primary, f.coerceIn(0f, 1f))
    val colorDe: (Element) -> Color = when (capa) {
        Capa.MUNICIPIS -> { e -> if (e.id in progres.municipisVisitats) cs.primary else buit }
        Capa.COMARQUES -> { e -> escala(progres.fraccioComarca(geo, e.id)) }
        Capa.VEGUERIES -> { e -> escala(progres.fraccioVegueria(geo, e.id)) }
    }

    Column(Modifier.fillMaxSize()) {
        // Barra de cerca + compartir
        if (!complet) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = cerca,
                onValueChange = { cerca = it },
                placeholder = { Text("Cerca un municipi") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (cerca.isNotEmpty()) IconButton(onClick = { cerca = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Esborra la cerca")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                ),
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = onComparteix, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Share, contentDescription = "Comparteix els llocs visitats")
            }
        }

        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                // Comptador
                if (!complet) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Has visitat", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        Text(milers(num), style = XifraGran, color = cs.primary)
                        Text("de ${milers(total)} $unitat", style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant)
                    }
                    Galeta(percent(num, total), 84.dp)
                }
                Surface(
                    color = cs.surfaceContainer,
                    shape = if (complet) RoundedCornerShape(0.dp) else RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (complet) Modifier else Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        MapaVectorial(
                            model = model,
                            capa = capa,
                            camera = camera,
                            colorDe = colorDe,
                            seleccionat = seleccionat,
                            onToc = { seleccionat = it?.id; focus.clearFocus() },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Controls dins del mapa: capes i pantalla completa
                        Row(
                            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                color = cs.surfaceContainerHigh.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(28.dp),
                                shadowElevation = 4.dp,
                                modifier = Modifier.weight(1f),
                            ) {
                                GrupOpcions(
                                    opcions = Capa.entries,
                                    seleccionada = capa,
                                    etiqueta = { it.etiqueta },
                                    onTria = { capa = it; seleccionat = null },
                                    modifier = Modifier.padding(4.dp),
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { onPantallaCompleta(!complet) },
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(
                                    if (complet) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = if (complet) "Surt de la pantalla completa" else "Mapa a pantalla completa",
                                )
                            }
                        }
                        if (complet) {
                            Surface(
                                color = cs.primary,
                                shape = RoundedCornerShape(20.dp),
                                shadowElevation = 4.dp,
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 76.dp),
                            ) {
                                Text(
                                    "${milers(num)} de ${milers(total)} $unitat · ${percent(num, total)}",
                                    color = cs.onPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Suggeriments de cerca
            val resultats = remember(cerca, geo) { geo.cerca(cerca) }
            if (!complet && resultats.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerHigh,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column {
                        resultats.forEachIndexed { i, m ->
                            if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        capa = Capa.MUNICIPIS
                                        seleccionat = m.id
                                        model.municipi[m.id]?.let { camera.centra(it.centre) }
                                        cerca = ""
                                        focus.clearFocus()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.nom, style = MaterialTheme.typography.titleMedium)
                                    Text("${geo.comarcaDe(m).nom} · ${geo.territoriDe(m).nom}",
                                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                if (m.id in estat.visitats) Icon(Icons.Filled.Check, contentDescription = "Visitat", tint = cs.primary)
                            }
                        }
                    }
                }
            }

            // Fitxa de l'element seleccionat
            androidx.compose.animation.AnimatedVisibility(
                visible = seleccionat != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val id = seleccionat
                if (id != null) Fitxa(geo, progres, capa, id, estat, onCommuta, onObreComarca) { seleccionat = null }
            }
        }
    }
}

@Composable
private fun Fitxa(
    geo: Geo,
    progres: Progres,
    capa: Capa,
    id: String,
    estat: Estat,
    onCommuta: (String) -> Unit,
    onObreComarca: (String) -> Unit,
    onTanca: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainerHighest,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    when (capa) {
                        Capa.MUNICIPIS -> geo.municipi[id]?.let { m ->
                            Text(m.nom, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                            val v = geo.vegueriaDe(m)?.let { " · Vegueria de ${it.nom}" } ?: ""
                            Text("${geo.comarcaDe(m).nom}$v · ${geo.territoriDe(m).nom}",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        Capa.COMARQUES -> geo.comarca[id]?.let { c ->
                            Text(c.nom, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                            val total = geo.municipisDeComarca[c.id]?.size ?: 0
                            Text("${progres.perComarca[c.id] ?: 0} de $total municipis visitats",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        Capa.VEGUERIES -> geo.vegueria[id]?.let { v ->
                            Text("Vegueria de ${v.nom}", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold)
                            val cs2 = geo.comarquesDeVegueria[v.id].orEmpty()
                            Text("${cs2.count { it.id in progres.comarquesVisitades }} de ${cs2.size} comarques visitades",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
                IconButton(onClick = onTanca) { Icon(Icons.Filled.Close, contentDescription = "Tanca") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (capa) {
                    Capa.MUNICIPIS -> {
                        val m = geo.municipi[id]
                        if (m != null) {
                            val visitat = id in estat.visitats
                            if (visitat) {
                                FilledTonalButton(onClick = { onCommuta(id) }) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Text("  Visitat")
                                }
                            } else {
                                Button(onClick = { onCommuta(id) }) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Text("  Marca com a visitat")
                                }
                            }
                            FilledTonalButton(onClick = { onObreComarca(m.comarca) }) { Text("Comarca") }
                        }
                    }
                    Capa.COMARQUES -> Button(onClick = { onObreComarca(id) }) { Text("Veure municipis") }
                    Capa.VEGUERIES -> Box(
                        Modifier.background(cs.secondaryContainer, RoundedCornerShape(12.dp)).padding(12.dp, 8.dp),
                    ) { Text("Toca la capa Comarques per veure-hi el detall", color = cs.onSecondaryContainer) }
                }
            }
        }
    }
}
