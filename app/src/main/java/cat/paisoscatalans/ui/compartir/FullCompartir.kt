package cat.paisoscatalans.ui.compartir

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cat.paisoscatalans.compartir.Compartir
import cat.paisoscatalans.compartir.Desti
import cat.paisoscatalans.compartir.Format
import cat.paisoscatalans.compartir.Opcions
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.Progres
import cat.paisoscatalans.ui.GrupOpcions
import cat.paisoscatalans.ui.mapa.ModelMapa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun iconaDe(d: Desti?): ImageVector = when (d) {
    Desti.INSTAGRAM -> Icons.Filled.Slideshow
    Desti.SIGNAL -> Icons.AutoMirrored.Filled.Chat
    Desti.MOLLY -> Icons.Filled.Shield
    Desti.MASTODON -> Icons.Filled.AlternateEmail
    Desti.BLUESKY -> Icons.Filled.Cloud
    null -> Icons.Filled.MoreHoriz
}

@Composable
fun FullCompartir(
    geo: Geo,
    model: ModelMapa,
    progres: Progres,
    nom: String,
    onTanca: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var format by rememberSaveable { mutableStateOf(Format.HISTORIA) }
    var ambMapa by rememberSaveable { mutableStateOf(true) }
    var ambComarques by rememberSaveable { mutableStateOf(true) }
    var desti by rememberSaveable { mutableStateOf<Desti?>(Desti.INSTAGRAM) }
    var imatge by remember { mutableStateOf<Bitmap?>(null) }
    var generant by remember { mutableStateOf(false) }
    val opcions = Opcions(format, ambMapa, ambComarques)
    val text = remember(progres, ambComarques) { Compartir.text(geo, progres, ambComarques) }

    LaunchedEffect(opcions, progres, nom) {
        generant = true
        imatge = withContext(Dispatchers.Default) { Compartir.renderitza(geo, model, progres, nom, opcions) }
        generant = false
    }
    LaunchedEffect(format) {
        if (format == Format.TEXT && desti == Desti.INSTAGRAM) desti = Desti.MASTODON
    }

    ModalBottomSheet(onDismissRequest = onTanca, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Comparteix el teu mapa", style = MaterialTheme.typography.headlineSmall)
            GrupOpcions(Format.entries, format, { it.etiqueta }, { format = it })

            Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().height(320.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    val b = imatge
                    when {
                        format == Format.TEXT -> Surface(
                            color = cs.surface,
                            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                            modifier = Modifier.padding(24.dp),
                        ) { Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
                        b != null -> Image(
                            b.asImageBitmap(),
                            contentDescription = "Previsualització de la imatge",
                            modifier = Modifier.fillMaxHeight().padding(14.dp),
                        )
                        generant -> LoadingIndicator()
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ambMapa, onClick = { ambMapa = !ambMapa }, label = { Text("Inclou el mapa") },
                    enabled = format != Format.TEXT)
                FilterChip(selected = ambComarques, onClick = { ambComarques = !ambComarques },
                    label = { Text("Comarques i vegueries") })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (Desti.entries + listOf<Desti?>(null)).forEach { d ->
                    val deshabilitat = d == Desti.INSTAGRAM && format == Format.TEXT
                    val installada = d == null || Compartir.paquetInstallat(ctx, d) != null
                    val sel = d == desti && !deshabilitat
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(58.dp)
                            .clickable(enabled = !deshabilitat) { desti = d },
                    ) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .background(
                                    when {
                                        sel -> cs.primary
                                        deshabilitat -> cs.surfaceContainerHighest
                                        else -> cs.secondaryContainer
                                    },
                                    if (sel) MaterialShapes.Cookie6Sided.toShape() else RoundedCornerShape(16.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                iconaDe(d), contentDescription = null,
                                tint = when {
                                    sel -> cs.onPrimary
                                    deshabilitat -> cs.outline
                                    else -> cs.onSecondaryContainer
                                },
                            )
                        }
                        Text(
                            d?.etiqueta ?: "Més",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            color = if (deshabilitat) cs.outline else cs.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (!installada && d != Desti.BLUESKY && d != Desti.MASTODON) {
                            Text("no instal·lada", style = MaterialTheme.typography.labelSmall, color = cs.outline,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        val b = imatge
                        val ok = b != null && Compartir.desaGaleria(ctx, b)
                        Toast.makeText(ctx, if (ok) "Imatge desada a Imatges/PaisosCatalans" else "No s'ha pogut desar",
                            Toast.LENGTH_SHORT).show()
                    },
                    enabled = format != Format.TEXT && imatge != null,
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Filled.Download, contentDescription = "Desa la imatge") }
                FilledTonalIconButton(
                    onClick = {
                        Compartir.copia(ctx, text)
                        Toast.makeText(ctx, "Text copiat", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copia el text") }
                Button(
                    onClick = {
                        val error = Compartir.envia(ctx, desti, if (format == Format.TEXT) null else imatge, text)
                        if (error != null) Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
                    },
                    enabled = format == Format.TEXT || imatge != null,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(desti?.let { "Comparteix a ${it.etiqueta.substringBefore(' ')}" } ?: "Comparteix…")
                }
            }
        }
    }
}
