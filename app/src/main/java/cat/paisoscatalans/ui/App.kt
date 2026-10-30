package cat.paisoscatalans.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cat.paisoscatalans.AppViewModel
import cat.paisoscatalans.Carrega
import cat.paisoscatalans.data.Geo
import cat.paisoscatalans.data.JocDiari
import cat.paisoscatalans.data.Progres
import cat.paisoscatalans.ui.ajustos.PantallaAjustos
import cat.paisoscatalans.ui.benvinguda.PantallaBenvinguda
import cat.paisoscatalans.ui.compartir.FullCompartir
import cat.paisoscatalans.ui.joc.PantallaJoc
import cat.paisoscatalans.ui.mapa.ModelMapa
import cat.paisoscatalans.ui.mapa.PantallaMapa
import cat.paisoscatalans.ui.perfil.PantallaPerfil
import cat.paisoscatalans.ui.progres.PantallaComarca
import cat.paisoscatalans.ui.progres.PantallaProgres
import cat.paisoscatalans.ui.theme.ModeTema
import cat.paisoscatalans.ui.theme.TemaPaisos

private enum class Pestanya(val etiqueta: String, val actiu: ImageVector, val inactiu: ImageVector) {
    MAPA("Mapa", Icons.Filled.Map, Icons.Outlined.Map),
    PROGRES("Progrés", Icons.Filled.Insights, Icons.Outlined.Insights),
    JOC("Joc diari", Icons.Filled.Casino, Icons.Outlined.Casino),
    PERFIL("Perfil", Icons.Filled.Person, Icons.Outlined.Person),
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val estat by vm.estat.collectAsStateWithLifecycle()
    TemaPaisos(ModeTema.de(estat.tema)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Contingut(vm)
        }
    }
}

@Composable
private fun Contingut(vm: AppViewModel) {
    val carrega by vm.geo.collectAsStateWithLifecycle()
    when (val c = carrega) {
        Carrega.EnCurs -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        Carrega.SenseDades -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Aquesta compilació no inclou les dades dels mapes.\n" +
                    "Executa tools/build_data.py (o el flux «Dades» de GitHub Actions) i torna a compilar.",
                textAlign = TextAlign.Center,
            )
        }
        is Carrega.Llesta -> {
            val estat by vm.estat.collectAsStateWithLifecycle()
            if (!estat.introduit) {
                PantallaBenvinguda(
                    onComencaDeZero = { nom, article -> vm.acabaBenvinguda(nom, article) },
                    onRestaura = { vm.importaCopia(it) },
                )
            } else {
                AppAmbDades(vm, c.geo)
            }
        }
    }
}

@Composable
private fun AppAmbDades(vm: AppViewModel, geo: Geo) {
    val estat by vm.estat.collectAsStateWithLifecycle()
    val model = remember(geo) { ModelMapa(geo) }
    val progres = remember(geo, estat) { Progres(geo, estat) }
    var pestanya by rememberSaveable { mutableStateOf(Pestanya.MAPA) }
    var comarca by rememberSaveable { mutableStateOf<String?>(null) }
    var compartint by rememberSaveable { mutableStateOf(false) }
    var ajustos by rememberSaveable { mutableStateOf(false) }
    var mapaComplet by rememberSaveable { mutableStateOf(false) }
    val ambBarra = !(mapaComplet && pestanya == Pestanya.MAPA && comarca == null && !ajustos)
    val sistemaFosc = isSystemInDarkTheme()

    BackHandler(enabled = ajustos) { ajustos = false }
    BackHandler(enabled = !ajustos && comarca != null) { comarca = null }
    BackHandler(enabled = !ajustos && comarca == null && pestanya != Pestanya.MAPA) { pestanya = Pestanya.MAPA }

    val jocPendent = estat.partides[JocDiari.avui()]?.acabada != true

    Scaffold(
        bottomBar = {
            if (ambBarra) ShortNavigationBar {
                Pestanya.entries.forEach { p ->
                    ShortNavigationBarItem(
                        selected = pestanya == p,
                        onClick = { pestanya = p; comarca = null; ajustos = false },
                        icon = {
                            Box {
                                Icon(if (pestanya == p) p.actiu else p.inactiu, contentDescription = null)
                                if (p == Pestanya.JOC && jocPendent) {
                                    androidx.compose.material3.Badge(Modifier.align(Alignment.TopEnd))
                                }
                            }
                        },
                        label = { Text(p.etiqueta) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Top,
        ) {
            AnimatedContent(
                targetState = Triple(ajustos, comarca, pestanya),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pantalla",
            ) { (aju, com, pes) ->
                if (aju) {
                    PantallaAjustos(
                        geo,
                        estat = estat,
                        llegeixFoto = { vm.llegeixFoto() },
                        onFoto = { vm.desaFoto(it) },
                        onTreuFoto = { vm.treuFoto() },
                        onNom = { vm.canviaNom(it) },
                        onArticle = { vm.canviaArticle(it) },
                        tema = ModeTema.de(estat.tema),
                        sistemaFosc = sistemaFosc,
                        onTema = { vm.canviaTema(it.clau) },
                        onEsborraTot = { vm.esborraTot() },
                        onExporta = { vm.exportaCopia(it) },
                        onImporta = { vm.importaCopia(it) },
                        onEnrere = { ajustos = false },
                    )
                } else if (com != null) {
                    PantallaComarca(
                        geo, model, estat, com,
                        onEnrere = { comarca = null },
                        onCommuta = { vm.commutaVisitat(it) },
                        onMarcaTots = { vm.marcaVisitats(it) },
                    )
                } else when (pes) {
                    Pestanya.MAPA -> PantallaMapa(
                        geo, model, estat, progres,
                        onCommuta = { vm.commutaVisitat(it) },
                        onComparteix = { compartint = true },
                        onObreComarca = { comarca = it; mapaComplet = false },
                        pantallaCompleta = mapaComplet,
                        onPantallaCompleta = { mapaComplet = it },
                    )
                    Pestanya.PROGRES -> PantallaProgres(
                        geo, progres,
                        onObreComarca = { comarca = it },
                        onComparteix = { compartint = true },
                    )
                    Pestanya.JOC -> PantallaJoc(
                        geo, model, estat,
                        onEndevina = { vm.endevina(geo, it) },
                        onPista = { vm.obrePista() },
                        onMarcaVisitat = { vm.commutaVisitat(it) },
                    )
                    Pestanya.PERFIL -> PantallaPerfil(
                        geo, estat, progres,
                        llegeixFoto = { vm.llegeixFoto() },
                        onComparteix = { compartint = true },
                        onAjustos = { ajustos = true },
                    )
                }
            }
        }
    }

    if (compartint) {
        FullCompartir(geo, model, progres, estat.nom, estat.article, onTanca = { compartint = false })
    }
}
