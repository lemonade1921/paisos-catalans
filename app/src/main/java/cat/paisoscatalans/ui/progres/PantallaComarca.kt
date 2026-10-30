package cat.paisoscatalans.ui.progres

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
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
import cat.paisoscatalans.data.Estat
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.ui.GrupOpcions
import cat.paisoscatalans.ui.mapa.ModelMapa
import cat.paisoscatalans.ui.mapa.Silueta
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Filtre(val etiqueta: String) { TOTS("Tots"), VISITATS("Visitats"), PENDENTS("Pendents") }

private val formatData = DateTimeFormatter.ofPattern("d MMMM 'de' yyyy", Locale.forLanguageTag("ca"))

fun dataLlegible(dia: Long): String = LocalDate.ofEpochDay(dia).format(formatData)

@Composable
fun PantallaComarca(
    geo: Geo,
    model: ModelMapa,
    estat: Estat,
    comarcaId: String,
    onEnrere: () -> Unit,
    onCommuta: (String) -> Unit,
    onMarcaTots: (List<String>) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val comarca = geo.comarca[comarcaId] ?: return
    val municipis = geo.municipisDeComarca[comarcaId].orEmpty()
    var filtre by rememberSaveable { mutableStateOf(Filtre.TOTS) }
    val fets = municipis.count { it.id in estat.visitats }
    val vegueria = comarca.vegueria?.let { geo.vegueria[it]?.nom }
    val territori = geo.territori[comarca.territori]?.nom ?: ""

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onEnrere) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna")
                    }
                    Text(
                        vegueria?.let { "Vegueria de $it" } ?: territori,
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(comarca.nom, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                        Text(territori, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                    }
                    Silueta(
                        municipis.mapNotNull { m ->
                            model.municipi[m.id]?.let { it to if (m.id in estat.visitats) cs.primary else cs.surfaceContainerHighest }
                        },
                        modifier = Modifier.size(96.dp),
                        vora = cs.surface,
                    )
                }
            }
            item {
                Surface(color = cs.secondaryContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$fets", style = MaterialTheme.typography.displayMedium, color = cs.onSecondaryContainer)
                            Text("  de ${municipis.size} municipis visitats", style = MaterialTheme.typography.titleMedium,
                                color = cs.onSecondaryContainer, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        LinearWavyProgressIndicator(
                            progress = { if (municipis.isEmpty()) 0f else fets / municipis.size.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                GrupOpcions(Filtre.entries, filtre, { it.etiqueta }, { filtre = it },
                    Modifier.padding(vertical = 12.dp))
            }
            val llista = municipis.filter {
                when (filtre) {
                    Filtre.TOTS -> true
                    Filtre.VISITATS -> it.id in estat.visitats
                    Filtre.PENDENTS -> it.id !in estat.visitats
                }
            }
            items(llista, key = { it.id }) { m ->
                val dia = estat.visitats[m.id]
                Surface(
                    color = cs.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.clickable { onCommuta(m.id) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = dia != null, onCheckedChange = { onCommuta(m.id) })
                        Column(Modifier.weight(1f)) {
                            Text(m.nom, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (dia != null) "Visitat el ${dataLlegible(dia)}" else "Pendent",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (fets < municipis.size) {
            ExtendedFloatingActionButton(
                onClick = { onMarcaTots(municipis.map { it.id }) },
                icon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                text = { Text("Marca'ls tots") },
                containerColor = cs.tertiaryContainer,
                contentColor = cs.onTertiaryContainer,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}
