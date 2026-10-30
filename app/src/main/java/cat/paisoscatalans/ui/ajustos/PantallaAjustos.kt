package cat.paisoscatalans.ui.ajustos

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.paisoscatalans.data.Estat
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.recordatori.Recordatori
import cat.paisoscatalans.ui.perfil.EditorPerfil
import cat.paisoscatalans.ui.theme.ModeTema
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Forma d'un element dins d'un grup de files (cantonades grans només als extrems). */
private fun formaGrup(i: Int, n: Int): Shape = when {
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

private fun hora(minuts: Int) = "%02d:%02d".format(minuts / 60, minuts % 60)

@Composable
fun PantallaAjustos(
    geo: Geo,
    estat: Estat,
    llegeixFoto: suspend () -> Bitmap?,
    onFoto: (Uri) -> Unit,
    onTreuFoto: () -> Unit,
    onNom: (String) -> Unit,
    onArticle: (String) -> Unit,
    tema: ModeTema,
    sistemaFosc: Boolean,
    onTema: (ModeTema) -> Unit,
    confeti: Boolean,
    onConfeti: (Boolean) -> Unit,
    recordatori: Boolean,
    horaRecordatori: Int,
    onRecordatori: (Boolean, Int) -> Unit,
    onEsborraTot: () -> Unit,
    onExporta: suspend (Uri) -> String?,
    onImporta: suspend (Uri) -> String?,
    onEnrere: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmaEsborrar by remember { mutableStateOf(false) }
    var perRestaurar by remember { mutableStateOf<Uri?>(null) }
    var triantHora by remember { mutableStateOf(false) }

    val desa = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val error = onExporta(uri)
            Toast.makeText(ctx, error ?: "Còpia de seguretat desada", Toast.LENGTH_LONG).show()
        }
    }
    val obre = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perRestaurar = uri
    }
    val permis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { donat ->
        if (donat) {
            onRecordatori(true, horaRecordatori)
        } else {
            Toast.makeText(ctx, "Sense permís de notificacions no et podem avisar", Toast.LENGTH_LONG).show()
        }
    }
    val activaRecordatori: (Boolean) -> Unit = { on ->
        if (on && Build.VERSION.SDK_INT >= 33 && !Recordatori.potNotificar(ctx)) {
            permis.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onRecordatori(on, horaRecordatori)
        }
    }

    val colorsFila = ListItemDefaults.colors(containerColor = Color.Transparent)
    val nJoc = if (recordatori) 3 else 2

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                FilledTonalIconButton(onClick = onEnrere) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna")
                }
                Text("Ajustos", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 16.dp))
            }
        }

        // ---- Perfil
        item { Titol("Perfil") }
        item {
            Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(16.dp)) {
                    EditorPerfil(estat, llegeixFoto, onFoto, onTreuFoto, onNom, onArticle)
                }
            }
        }

        // ---- Aparença
        item { Titol("Aparença") }
        ModeTema.entries.forEachIndexed { i, m ->
            item(key = "tema-${m.clau}") {
                val sel = m == tema
                Fila(i, ModeTema.entries.size, onClick = { onTema(m) }, seleccionada = sel) {
                    ListItem(
                        colors = colorsFila,
                        leadingContent = { RadioButton(selected = sel, onClick = null) },
                        headlineContent = { Text(m.etiqueta) },
                        supportingContent = { Text(m.descripcio) },
                        trailingContent = {
                            val (fons, sup, accent) = mostra(m, sistemaFosc)
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(fons, RoundedCornerShape(12.dp))
                                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(24.dp).background(sup, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(10.dp).background(accent, CircleShape))
                                }
                            }
                        },
                    )
                }
            }
        }

        // ---- Joc i insígnies
        item { Titol("Joc i insígnies") }
        item {
            Fila(0, nJoc, onClick = { activaRecordatori(!recordatori) }) {
                ListItem(
                    colors = colorsFila,
                    leadingContent = { Icon(Icons.Filled.NotificationsActive, contentDescription = null) },
                    headlineContent = { Text("Recordatori diari") },
                    supportingContent = { Text("Un avís cada dia si encara no has jugat al municipi del dia") },
                    trailingContent = { Switch(checked = recordatori, onCheckedChange = { activaRecordatori(it) }) },
                )
            }
        }
        if (recordatori) {
            item {
                Fila(1, nJoc, onClick = { triantHora = true }) {
                    ListItem(
                        colors = colorsFila,
                        leadingContent = { Icon(Icons.Filled.Alarm, contentDescription = null) },
                        headlineContent = { Text("Hora de l'avís") },
                        supportingContent = { Text("Toca per canviar-la") },
                        trailingContent = {
                            Text(hora(horaRecordatori), style = MaterialTheme.typography.headlineSmall, color = cs.primary)
                        },
                    )
                }
            }
        }
        item {
            Fila(nJoc - 1, nJoc, onClick = { onConfeti(!confeti) }) {
                ListItem(
                    colors = colorsFila,
                    leadingContent = { Icon(Icons.Filled.Celebration, contentDescription = null) },
                    headlineContent = { Text("Confeti") },
                    supportingContent = { Text("Celebra amb confeti cada insígnia nova") },
                    trailingContent = { Switch(checked = confeti, onCheckedChange = onConfeti) },
                )
            }
        }

        // ---- Còpia de seguretat
        item { Titol("Còpia de seguretat") }
        item {
            Fila(0, 2, onClick = { desa.launch("paisos-catalans-${LocalDate.now()}.json") }) {
                ListItem(
                    colors = colorsFila,
                    leadingContent = { Icon(Icons.Filled.Save, contentDescription = null) },
                    headlineContent = { Text("Desa una còpia") },
                    supportingContent = { Text("Llocs visitats, partides i ratxes, insígnies, perfil i ajustos en un fitxer") },
                )
            }
        }
        item {
            Fila(1, 2, onClick = { obre.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                ListItem(
                    colors = colorsFila,
                    leadingContent = { Icon(Icons.Filled.RestorePage, contentDescription = null) },
                    headlineContent = { Text("Restaura una còpia") },
                    supportingContent = { Text("Substitueix les dades actuals per les d'un fitxer de còpia") },
                )
            }
        }

        // ---- Privadesa i dades
        item { Titol("Privadesa i dades") }
        item {
            Fila(0, 3) {
                ListItem(
                    colors = colorsFila,
                    leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    headlineContent = { Text("Tot es queda al telèfon") },
                    supportingContent = { Text("Sense connexió a Internet, sense comptes, anuncis ni estadístiques d'ús.") },
                )
            }
        }
        item {
            Fila(1, 3) {
                ListItem(
                    colors = colorsFila,
                    leadingContent = { Icon(Icons.Filled.Map, contentDescription = null) },
                    headlineContent = { Text("Dades dels mapes") },
                    supportingContent = {
                        Text(
                            "${geo.territoris.joinToString(", ") { it.nom }}. Només municipis de parla catalana " +
                                "(sense la Vall d'Aran ni la Fenolleda, que són occitanoparlants). " +
                                "${geo.municipis.size} municipis, ${geo.comarques.size} comarques, " +
                                "${geo.vegueries.size} vegueries. ${geo.atribucio} · ${geo.versio}",
                        )
                    },
                )
            }
        }
        item {
            Fila(2, 3, onClick = { confirmaEsborrar = true }) {
                ListItem(
                    colors = colorsFila,
                    leadingContent = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = cs.error) },
                    headlineContent = { Text("Esborra totes les dades", color = cs.error) },
                    supportingContent = { Text("Nom, foto, llocs visitats i historial del joc") },
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text("  Països Catalans · versió 0.5.0", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }

    if (triantHora) {
        val estatHora = rememberTimePickerState(
            initialHour = horaRecordatori / 60,
            initialMinute = horaRecordatori % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { triantHora = false },
            title = { Text("Hora del recordatori") },
            text = { TimePicker(state = estatHora) },
            confirmButton = {
                TextButton(onClick = {
                    triantHora = false
                    onRecordatori(true, estatHora.hour * 60 + estatHora.minute)
                }) { Text("D'acord") }
            },
            dismissButton = { TextButton(onClick = { triantHora = false }) { Text("Cancel·la") } },
        )
    }

    perRestaurar?.let { uri ->
        AlertDialog(
            onDismissRequest = { perRestaurar = null },
            title = { Text("Restaurar la còpia?") },
            text = { Text("Les dades actuals se substituiran per les de la còpia de seguretat.") },
            confirmButton = {
                TextButton(onClick = {
                    perRestaurar = null
                    scope.launch {
                        val error = onImporta(uri)
                        Toast.makeText(ctx, error ?: "Còpia restaurada", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Restaura") }
            },
            dismissButton = { TextButton(onClick = { perRestaurar = null }) { Text("Cancel·la") } },
        )
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
private fun Titol(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 6.dp),
    )
}

/** Una fila dins d'un grup, amb la forma segmentada d'M3 Expressive. */
@Composable
private fun Fila(
    i: Int,
    n: Int,
    onClick: (() -> Unit)? = null,
    seleccionada: Boolean = false,
    contingut: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (seleccionada) cs.secondaryContainer else cs.surfaceContainer,
        shape = formaGrup(i, n),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column { contingut() }
    }
}
