package cat.paisoscatalans.ui.compartir

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import cat.paisoscatalans.compartir.Compartir
import cat.paisoscatalans.compartir.Desti
import cat.paisoscatalans.compartir.Extra
import cat.paisoscatalans.compartir.Format
import cat.paisoscatalans.compartir.Opcions
import cat.paisoscatalans.compartir.Paleta
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.Progres
import cat.paisoscatalans.ui.GrupOpcions
import cat.paisoscatalans.ui.LocalEscuts
import cat.paisoscatalans.ui.mapa.ModelMapa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opcions de la imatge que es poden activar o desactivar. */
private enum class Opcio(val etiqueta: String, val nomesImatge: Boolean = true, val ambMapa: Boolean = false) {
    MAPA("Mapa", ambMapa = true),
    CONTORNS("Contorns", ambMapa = true),
    NOM("Nom"),
    PERCENTATGE("Percentatge"),
    COMARQUES("Comarques i vegueries", nomesImatge = false),
    ESCUTS("Escuts"),
    DATA("Data"),
}

/**
 * Full per compartir. Tot hi cap sense desplaçar-se: previsualització, format, opcions
 * (en una fila lliscant), colors i només les apps que tens instal·lades.
 */
@Composable
fun FullCompartir(
    geo: Geo,
    model: ModelMapa,
    progres: Progres,
    nom: String,
    article: String,
    extra: Extra = Extra(),
    formatInicial: Format = Format.HISTORIA,
    onTanca: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val escuts = LocalEscuts.current
    var format by rememberSaveable { mutableStateOf(formatInicial) }
    var actives by remember {
        mutableStateOf(setOf(Opcio.MAPA, Opcio.CONTORNS, Opcio.NOM, Opcio.PERCENTATGE, Opcio.COMARQUES))
    }
    var paleta by rememberSaveable { mutableStateOf(Paleta.TERRACOTA) }
    // Només les apps instal·lades (amb la icona real)
    val resolts = remember { Desti.entries.associateWith { Compartir.resol(ctx, it) } }
    val destins = remember(resolts) { Desti.entries.filter { resolts[it]?.paquet != null } }
    val icones = remember(resolts) {
        resolts.mapValues { (_, r) -> r.icona?.let { runCatching { it.toBitmap(112, 112).asImageBitmap() }.getOrNull() } }
    }
    var desti by remember { mutableStateOf(destins.firstOrNull()) }
    var imatge by remember { mutableStateOf<Bitmap?>(null) }
    var generant by remember { mutableStateOf(false) }
    val opcions = Opcions(
        format = format,
        mapa = Opcio.MAPA in actives,
        comarques = Opcio.COMARQUES in actives,
        paleta = paleta,
        nom = Opcio.NOM in actives,
        percentatge = Opcio.PERCENTATGE in actives,
        escuts = Opcio.ESCUTS in actives,
        data = Opcio.DATA in actives,
        contorns = Opcio.CONTORNS in actives,
    )
    val text = remember(progres, opcions.comarques) { Compartir.text(geo, progres, opcions.comarques) }
    val bitmapsEscuts = remember(escuts) { escuts.mapValues { it.value.imatge.asAndroidBitmap() } }

    LaunchedEffect(opcions, progres, nom, article) {
        generant = true
        imatge = withContext(Dispatchers.Default) {
            Compartir.renderitza(geo, model, progres, nom, article, opcions, bitmapsEscuts, extra)
        }
        generant = false
    }
    LaunchedEffect(format) {
        if (format == Format.TEXT && desti?.nomesImatge == true) desti = destins.firstOrNull { !it.nomesImatge }
    }

    ModalBottomSheet(onDismissRequest = onTanca, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GrupOpcions(Format.entries, format, { it.etiqueta }, { format = it })

            Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(250.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    val b = imatge
                    when {
                        format == Format.TEXT -> Surface(
                            color = cs.surface,
                            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                            modifier = Modifier.padding(20.dp),
                        ) { Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
                        b != null -> Image(
                            b.asImageBitmap(),
                            contentDescription = "Previsualització de la imatge",
                            modifier = Modifier.fillMaxHeight().padding(10.dp),
                        )
                        generant -> LoadingIndicator()
                    }
                }
            }

            // Opcions de la imatge, en una sola fila que llisca
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Opcio.entries.filter { it != Opcio.ESCUTS || escuts.isNotEmpty() }.forEach { o ->
                    FilterChip(
                        selected = o in actives,
                        onClick = { actives = if (o in actives) actives - o else actives + o },
                        label = { Text(o.etiqueta) },
                        enabled = (format != Format.TEXT || !o.nomesImatge) && !(format == Format.RESUM && o.ambMapa),
                    )
                }
            }

            // Colors
            if (format != Format.TEXT) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Paleta.entries.forEach { p ->
                        val sel = p == paleta
                        val forma = if (sel) MaterialShapes.Cookie9Sided.toShape() else CircleShape
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(forma)
                                .background(Color(p.fons))
                                .border(if (sel) 3.dp else 1.dp, if (sel) cs.primary else cs.outlineVariant, forma)
                                .clickable(onClickLabel = p.etiqueta) { paleta = p },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(16.dp).background(Color(p.visitat), CircleShape))
                        }
                    }
                }
            }

            // Destinacions: només les apps instal·lades, més «Més»
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (destins + listOf<Desti?>(null)).forEach { d ->
                    val deshabilitat = d?.nomesImatge == true && format == Format.TEXT
                    val sel = d == desti && !deshabilitat
                    val icona = d?.let { icones[it] }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(68.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !deshabilitat) { desti = d }
                            .padding(vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(54.dp)
                                .then(
                                    if (sel) Modifier.border(3.dp, cs.primary, MaterialShapes.Cookie6Sided.toShape())
                                    else Modifier,
                                )
                                .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (icona != null) {
                                Image(icona, contentDescription = null, alpha = if (deshabilitat) 0.4f else 1f,
                                    modifier = Modifier.fillMaxSize())
                            } else {
                                Box(
                                    Modifier.fillMaxSize().background(
                                        if (sel) cs.primary else cs.secondaryContainer, RoundedCornerShape(14.dp),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.MoreHoriz, contentDescription = null,
                                        tint = if (sel) cs.onPrimary else cs.onSecondaryContainer)
                                }
                            }
                        }
                        Text(
                            d?.etiqueta ?: "Més",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            color = if (sel) cs.primary else if (deshabilitat) cs.outline else cs.onSurface,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        val b = imatge
                        val ok = b != null && Compartir.desaGaleria(ctx, b)
                        Toast.makeText(ctx, if (ok) "Imatge desada a Imatges/PaisosCatalans" else "No s'ha pogut desar",
                            Toast.LENGTH_SHORT).show()
                    },
                    enabled = format != Format.TEXT && imatge != null,
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Filled.Download, contentDescription = "Desa la imatge") }
                FilledTonalIconButton(
                    onClick = {
                        Compartir.copia(ctx, text)
                        Toast.makeText(ctx, "Text copiat", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copia el text") }
                Button(
                    onClick = {
                        val error = Compartir.envia(ctx, desti, if (format == Format.TEXT) null else imatge, text)
                        if (error != null) Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
                    },
                    enabled = format == Format.TEXT || imatge != null,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(desti?.let { "Comparteix a ${it.etiqueta}" } ?: "Comparteix…", maxLines = 1)
                }
            }
        }
    }
}
