package cat.paisoscatalans.ui.perfil

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cat.paisoscatalans.data.Categoria
import cat.paisoscatalans.data.Estat
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.Insignia
import cat.paisoscatalans.data.Insignies
import cat.paisoscatalans.data.JocDiari
import cat.paisoscatalans.data.Progres
import cat.paisoscatalans.data.Ratxa
import cat.paisoscatalans.ui.Seccio

@Composable
internal fun formaDe(c: Categoria): Shape = when (c) {
    Categoria.MUNICIPIS -> MaterialShapes.Cookie9Sided.toShape()
    Categoria.COMARQUES -> MaterialShapes.Clover4Leaf.toShape()
    Categoria.VEGUERIES -> MaterialShapes.Sunny.toShape()
    Categoria.TERRITORIS -> MaterialShapes.Pentagon.toShape()
    Categoria.RATXA -> MaterialShapes.Flower.toShape()
}

internal fun iconaDe(c: Categoria): ImageVector = when (c) {
    Categoria.MUNICIPIS -> Icons.Filled.Place
    Categoria.COMARQUES -> Icons.Filled.Map
    Categoria.VEGUERIES -> Icons.Filled.Explore
    Categoria.TERRITORIS -> Icons.Filled.Public
    Categoria.RATXA -> Icons.Filled.LocalFireDepartment
}

@Composable
fun PantallaPerfil(
    geo: Geo,
    estat: Estat,
    progres: Progres,
    llegeixFoto: suspend () -> Bitmap?,
    onFoto: (Uri) -> Unit,
    onTreuFoto: () -> Unit,
    onNom: (String) -> Unit,
    onComparteix: () -> Unit,
    onAjustos: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(estat.fotoVersio, estat.teFoto) { foto = if (estat.teFoto) llegeixFoto() else null }
    val tria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onFoto(uri)
    }
    var nom by rememberSaveable(estat.nom) { mutableStateOf(estat.nom) }
    var oberta by remember { mutableStateOf<Insignia?>(null) }

    val dia = JocDiari.avui()
    val ratxa = Ratxa.actual(estat, dia)
    val ratxaMax = Ratxa.maxima(estat)
    val insignies = remember(geo, progres, ratxaMax) { Insignies.calcula(geo, progres, ratxaMax) }
    val aconseguides = insignies.count { it.aconseguida }
    val avatar = MaterialShapes.Cookie9Sided.toShape()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Perfil", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = onComparteix) {
                    Icon(Icons.Filled.Share, contentDescription = "Comparteix")
                }
                FilledTonalIconButton(onClick = onAjustos) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustos")
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(132.dp)
                        .clip(avatar)
                        .background(cs.primaryContainer)
                        .clickable {
                            tria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val f = foto
                    if (f != null) {
                        Image(f.asImageBitmap(), contentDescription = "Foto de perfil",
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else if (nom.isNotBlank()) {
                        Text(nom.trim().take(1).uppercase(), style = MaterialTheme.typography.displayMedium,
                            color = cs.onPrimaryContainer)
                    } else {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = "Tria una foto de perfil",
                            tint = cs.onPrimaryContainer, modifier = Modifier.size(40.dp))
                    }
                }
                Row {
                    TextButton(onClick = {
                        tria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (foto == null) "Tria una foto" else "Canvia la foto") }
                    if (foto != null) TextButton(onClick = onTreuFoto) { Text("Treu-la") }
                }
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it.take(40) },
                    label = { Text("El teu nom") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onNom(nom); focus.clearFocus() }),
                    trailingIcon = {
                        if (nom != estat.nom) TextButton(onClick = { onNom(nom); focus.clearFocus() }) { Text("Desa") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Xifra("${progres.municipisVisitats.size}", "municipis", Modifier.weight(1f))
                Xifra("${progres.comarquesVisitades.size}", "comarques", Modifier.weight(1f))
                Xifra("${progres.vegueriesVisitades.size}", "vegueries", Modifier.weight(1f))
                Xifra("$ratxa", "ratxa (màx. $ratxaMax)", Modifier.weight(1f))
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Text("Insígnies", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text("$aconseguides de ${insignies.size}", color = cs.onSurfaceVariant)
            }
        }

        Categoria.entries.forEach { cat ->
            val llista = insignies.filter { it.categoria == cat }
            if (llista.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Seccio(cat.titol) }
                items(llista, key = { it.id }) { ins ->
                    TargetaInsignia(ins, formaDe(cat), iconaDe(cat)) { oberta = ins }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Toca una insígnia per veure-la en 3D.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }

    oberta?.let { ins ->
        DialegInsignia(ins, formaDe(ins.categoria), iconaDe(ins.categoria), ins.categoria.titol) { oberta = null }
    }
}

@Composable
private fun Xifra(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valor, style = MaterialTheme.typography.headlineMedium, color = cs.primary, fontWeight = FontWeight.Black)
            Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TargetaInsignia(i: Insignia, forma: Shape, icona: ImageVector, onToca: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fons = if (i.aconseguida) cs.tertiaryContainer else cs.surfaceContainerHighest
    val tinta = if (i.aconseguida) cs.onTertiaryContainer else cs.outline
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Mostra la medalla", onClick = onToca)
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Box(Modifier.size(72.dp).background(fons, forma), contentAlignment = Alignment.Center) {
            Icon(icona, contentDescription = i.descripcio, tint = tinta, modifier = Modifier.size(30.dp))
        }
        Text(
            i.titol, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            color = if (i.aconseguida) cs.onSurface else cs.onSurfaceVariant, maxLines = 2,
        )
        Text(
            if (i.aconseguida) "Aconseguida" else "${minOf(i.progres, i.objectiu)}/${i.objectiu}",
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}
