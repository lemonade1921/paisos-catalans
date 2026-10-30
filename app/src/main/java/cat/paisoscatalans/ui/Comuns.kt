package cat.paisoscatalans.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Grup de botons connectats (M3 Expressive) per triar una opció. */
@Composable
fun <T> GrupOpcions(
    opcions: List<T>,
    seleccionada: T,
    etiqueta: (T) -> String,
    onTria: (T) -> Unit,
    modifier: Modifier = Modifier,
    habilitada: (T) -> Boolean = { true },
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        opcions.forEachIndexed { i, o ->
            ToggleButton(
                checked = o == seleccionada,
                onCheckedChange = { onTria(o) },
                enabled = habilitada(o),
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    opcions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(etiqueta(o), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Fila amb nom, recompte i barra de progrés ondulada. */
@Composable
fun FilaProgres(
    nom: String,
    fet: Int,
    total: Int,
    modifier: Modifier = Modifier,
    unitat: String = "",
) {
    val f = if (total > 0) fet / total.toFloat() else 0f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nom, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$fet / $total$unitat", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearWavyProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
    }
}

/** Forma "galeta" expressiva amb un text al mig (percentatges, iniciales...). */
@Composable
fun Galeta(
    text: String,
    mida: Dp,
    color: Color = MaterialTheme.colorScheme.tertiaryContainer,
    colorText: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    forma: Shape = MaterialShapes.Cookie9Sided.toShape(),
) {
    Box(
        Modifier.size(mida).background(color, forma),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = colorText,
            fontWeight = FontWeight.Black,
            fontSize = (mida.value * 0.26f).sp,
            maxLines = 1,
        )
    }
}

@Composable
fun Seccio(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun Xip(text: String, color: Color, colorText: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = colorText,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(color, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun Separacio(h: Dp = 8.dp) = Spacer(Modifier.size(h))

@Composable
fun SeparacioH(w: Dp = 8.dp) = Spacer(Modifier.width(w))

fun percent(fet: Int, total: Int): String =
    if (total == 0) "0%" else "${(100.0 * fet / total).let { if (it > 0 && it < 1) 1 else it.toInt() }}%"

/** Formata nombres amb punt de milers, a la catalana: 1.783 */
fun milers(n: Int): String = "%,d".format(n).replace(',', '.')

/**
 * Tria de l'article personal en un desplegable. Només mostra les opcions que encaixen amb
 * el nom (L'/N' davant de vocal; El/En/La/Na davant de consonant) i adapta la tria si el
 * nom canvia.
 */
@Composable
fun SelectorArticle(
    nom: String,
    article: String,
    onTria: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val opcions = cat.paisoscatalans.data.Article.opcionsPer(nom)
    // Si el nom canvia de vocal a consonant (o al revés), l'article s'adapta tot sol
    val ajustat = cat.paisoscatalans.data.Article.ajusta(nom, article)
    androidx.compose.runtime.LaunchedEffect(nom, article) { if (ajustat != article) onTria(ajustat) }
    var obert by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val etiqueta = opcions.firstOrNull { it.first == ajustat }?.second ?: "Cap"
    val mostra = nom.trim().ifEmpty { "Maria" }

    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = obert,
        onExpandedChange = { obert = it },
        modifier = modifier,
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = etiqueta,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Article personal") },
            supportingText = { Text("«El mapa ${cat.paisoscatalans.data.Article.genitiu(mostra, ajustat)}»") },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = obert) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = obert, onDismissRequest = { obert = false }) {
            opcions.forEach { (clau, text) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onTria(clau); obert = false },
                )
            }
        }
    }
}
