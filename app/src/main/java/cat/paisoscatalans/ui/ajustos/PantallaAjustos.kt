package cat.paisoscatalans.ui.ajustos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.ui.theme.ModeTema

private fun formaGrup(i: Int, n: Int) = when {
    n == 1 -> RoundedCornerShape(24.dp)
    i == 0 -> RoundedCornerShape(24.dp, 24.dp, 6.dp, 6.dp)
    i == n - 1 -> RoundedCornerShape(6.dp, 6.dp, 24.dp, 24.dp)
    else -> RoundedCornerShape(6.dp)
}

/** Colors de mostra per a cada mode: (fons, superfície, accent). */
private fun mostra(m: ModeTema, sistemaFosc: Boolean): Triple<Color, Color, Color> = when (m) {
    ModeTema.CLAR -> Triple(Color(0xFFFFF8F6), Color(0xFFF5E4DE), Color(0xFF9A4521))
    ModeTema.FOSC -> Triple(Color(0xFF1A110F), Color(0xFF322825), Color(0xFFFFB59A))
    ModeTema.OLED -> Triple(Color.Black, Color(0xFF1C1513), Color(0xFFFFB59A))
    ModeTema.SISTEMA -> if (sistemaFosc) mostra(ModeTema.FOSC, true) else mostra(ModeTema.CLAR, false)
}

@Composable
fun PantallaAjustos(
    geo: Geo,
    tema: ModeTema,
    sistemaFosc: Boolean,
    onTema: (ModeTema) -> Unit,
    onEsborraTot: () -> Unit,
    onEnrere: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var confirmaEsborrar by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                FilledTonalIconButton(onClick = onEnrere) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna")
                }
                Text("Ajustos", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 16.dp))
            }
        }

        item { Capcalera(Icons.Filled.Palette, "Aparença") }
        item {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ModeTema.entries.forEachIndexed { i, m ->
                    val sel = m == tema
                    Surface(
                        color = if (sel) cs.secondaryContainer else cs.surfaceContainer,
                        shape = formaGrup(i, ModeTema.entries.size),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = sel, role = Role.RadioButton, onClick = { onTema(m) }),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sel, onClick = null)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(m.etiqueta, style = MaterialTheme.typography.titleMedium,
                                    color = if (sel) cs.onSecondaryContainer else cs.onSurface)
                                Text(m.descripcio, style = MaterialTheme.typography.bodySmall,
                                    color = if (sel) cs.onSecondaryContainer else cs.onSurfaceVariant)
                            }
                            val (fons, sup, accent) = mostra(m, sistemaFosc)
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(fons, RoundedCornerShape(14.dp))
                                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(28.dp).background(sup, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(12.dp).background(accent, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Capcalera(Icons.Filled.Map, "Dades dels mapes", Modifier.padding(top = 20.dp)) }
        item {
            Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Territoris inclosos: " + geo.territoris.joinToString(", ") { it.nom } + ".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Només hi ha els municipis de parla catalana: al País Valencià, els de predomini " +
                            "lingüístic valencià; a la Franja, els catalanoparlants. La Vall d'Aran i la Fenolleda " +
                            "són occitanoparlants i no hi són. El Carxe no té municipi propi i el seu límit és aproximat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Text(
                        "${geo.municipis.size} municipis · ${geo.comarques.size} comarques · " +
                            "${geo.vegueries.size} vegueries",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text("${geo.atribucio} · Dades del ${geo.versio}", style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant)
                }
            }
        }

        item { Capcalera(Icons.Filled.Lock, "Privadesa", Modifier.padding(top = 20.dp)) }
        item {
            Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Aquesta app funciona sense connexió i no té permís per accedir a Internet. " +
                            "El teu nom, la foto, els llocs visitats i les partides només es desen en aquest telèfon. " +
                            "No hi ha comptes, ni anuncis, ni estadístiques d'ús.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { confirmaEsborrar = true }) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = cs.error)
                        Text("  Esborra totes les dades", color = cs.error)
                    }
                }
            }
        }
    }

    if (confirmaEsborrar) {
        AlertDialog(
            onDismissRequest = { confirmaEsborrar = false },
            title = { Text("Esborrar-ho tot?") },
            text = { Text("S'esborraran el nom, la foto, tots els llocs visitats i l'historial del joc. No es pot desfer.") },
            confirmButton = {
                TextButton(onClick = { confirmaEsborrar = false; onEsborraTot() }) { Text("Esborra", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmaEsborrar = false }) { Text("Cancel·la") } },
        )
    }
}

@Composable
private fun Capcalera(icona: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(start = 4.dp, bottom = 8.dp)) {
        Icon(icona, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text("  $text", style = MaterialTheme.typography.titleMedium)
    }
}
