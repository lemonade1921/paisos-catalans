package cat.paisoscatalans.ui.progres

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.Progres
import cat.paisoscatalans.ui.FilaProgres
import cat.paisoscatalans.ui.Galeta
import cat.paisoscatalans.ui.Seccio
import cat.paisoscatalans.ui.Xip
import cat.paisoscatalans.ui.milers
import cat.paisoscatalans.ui.percent

private fun formaGrup(i: Int, n: Int) = when {
    n == 1 -> RoundedCornerShape(24.dp)
    i == 0 -> RoundedCornerShape(24.dp, 24.dp, 6.dp, 6.dp)
    i == n - 1 -> RoundedCornerShape(6.dp, 6.dp, 24.dp, 24.dp)
    else -> RoundedCornerShape(6.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaProgres(
    geo: Geo,
    progres: Progres,
    onObreComarca: (String) -> Unit,
    onComparteix: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var obert by rememberSaveable { mutableStateOf<String?>(null) }
    val nm = progres.municipisVisitats.size

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Text("Progrés", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onComparteix) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  Comparteix")
                }
            }
        }
        item {
            Surface(color = cs.primary, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Galeta(percent(nm, geo.municipis.size), 100.dp)
                    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$nm de ${milers(geo.municipis.size)} municipis", color = cs.onPrimary,
                            style = MaterialTheme.typography.titleLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Xip("${progres.comarquesVisitades.size} comarques", cs.onPrimary.copy(alpha = 0.16f), cs.onPrimary)
                            Xip("${progres.vegueriesVisitades.size} vegueries", cs.onPrimary.copy(alpha = 0.16f), cs.onPrimary)
                        }
                    }
                }
            }
        }
        item { Seccio("Per territori", Modifier.padding(top = 12.dp)) }
        geo.territoris.forEachIndexed { i, t ->
            item(key = "t-${t.id}") {
                val comarques = geo.comarquesDeTerritori[t.id].orEmpty()
                val unaSola = comarques.size == 1
                val esObert = obert == t.id
                Surface(
                    color = cs.surfaceContainer,
                    shape = formaGrup(i, geo.territoris.size),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Territoris d'una sola comarca (Andorra, el Carxe, l'Alguer):
                                    // s'obre directament la llista de municipis
                                    if (unaSola) onObreComarca(comarques.first().id)
                                    else obert = if (esObert) null else t.id
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                FilaProgres(
                                    t.nom,
                                    progres.perTerritori[t.id] ?: 0,
                                    geo.municipisDeTerritori[t.id]?.size ?: 0,
                                )
                                if (!unaSola) {
                                    val fetes = comarques.count { it.id in progres.comarquesVisitades }
                                    Text(
                                        "$fetes de ${comarques.size} comarques",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                            Icon(
                                when {
                                    unaSola -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                                    esObert -> Icons.Filled.ExpandLess
                                    else -> Icons.Filled.ExpandMore
                                },
                                contentDescription = when {
                                    unaSola -> "Mostra els municipis"
                                    esObert -> "Amaga les comarques"
                                    else -> "Mostra les comarques"
                                },
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        if (esObert && !unaSola) {
                            Surface(
                                color = cs.surfaceContainerHigh,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            ) {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    comarques.forEach { c ->
                                        val fet = progres.perComarca[c.id] ?: 0
                                        val total = geo.municipisDeComarca[c.id]?.size ?: 0
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { onObreComarca(c.id) }
                                                .padding(horizontal = 16.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(c.nom, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                                                maxLines = 1)
                                            if (fet == total && total > 0) {
                                                Icon(Icons.Filled.Check, contentDescription = "Completa", tint = cs.primary,
                                                    modifier = Modifier.padding(end = 6.dp))
                                            }
                                            Text("$fet/$total", color = cs.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelLarge)
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                                                tint = cs.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (geo.vegueries.isNotEmpty()) {
            item {
                Seccio("Vegueries del Principat · ${progres.vegueriesVisitades.size} de ${geo.vegueries.size}",
                    Modifier.padding(top = 12.dp))
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    geo.vegueries.forEach { v ->
                        val feta = v.id in progres.vegueriesVisitades
                        FilterChip(
                            selected = feta,
                            onClick = {},
                            label = { Text(v.nom) },
                            leadingIcon = if (feta) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(0.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
        }
        item {
            Text(
                geo.atribucio,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 24.dp, start = 4.dp),
            )
        }
    }
}
