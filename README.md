# Països Catalans

App per a Android per portar el compte dels **municipis**, **comarques** i **vegueries** que has visitat
al Principat de Catalunya, el País Valencià, Catalunya Nord, les Illes Balears i l'Alguer.
Inclou un joc diari per endevinar un municipi i insígnies per anar-ne guanyant.

Disseny: Material 3 Expressive (Jetpack Compose). Tota l'app és en català.

## Funcions

- **Mapa** vectorial amb tres capes (Municipis / Comarques / Vegueries), zoom i cerca.
  Toca un municipi per marcar-lo com a visitat.
- **Progrés** per territori i per comarca, amb la llista de municipis de cada comarca.
- **Joc diari: el municipi del dia.** Sis intents; cada resposta mostra la distància, la direcció i la proximitat.
  Pistes de territori i comarca. Ratxa de dies seguits jugant.
- **Perfil** amb nom, foto i **insígnies**: municipis, comarques, vegueries, territoris i ratxes del joc.
- **Comparteix** una imatge (format història o publicació) o un text a Instagram Stories, Signal, Molly,
  Mastodon i Bluesky, o desa la imatge a la galeria.

## Privadesa i funcionament sense connexió

- L'app **no té el permís d'Internet** (el manifest el treu explícitament). No hi ha comptes, anuncis ni analítiques.
- Els mapes van **dins l'APK** (`app/src/main/assets/geo.json`): no hi ha tessel·les ni descàrregues.
- El nom, la foto, els llocs visitats i les partides es desen només a l'emmagatzematge privat de l'app.
  Les còpies de seguretat al núvol estan desactivades.
- La foto de perfil es tria amb el selector de fotos del sistema (sense permís d'emmagatzematge) i se'n desa
  una còpia reduïda dins l'app.
- Compartir fa servir els intents d'Android: és l'app de destinació qui envia el contingut, no aquesta.
- El **municipi del dia es calcula al telèfon**: es barregen tots els municipis amb una llavor fixa i cada dia
  se n'agafa el següent. Tothom té el mateix repte el mateix dia i no es repeteix cap municipi fins que s'han jugat tots.

## Dades dels mapes

Els límits provenen d'**OpenStreetMap** (© col·laboradors d'OpenStreetMap, llicència
[ODbL](https://opendatacommons.org/licenses/odbl/)). L'script `tools/build_data.py`:

1. Descarrega via Overpass els municipis (`admin_level=8`) de cada territori.
2. Assigna la comarca de cada municipi:
   - Principat: comarques d'OSM (`admin_level=7`).
   - País Valencià: comarques d'OSM si n'hi ha prou; si no, les categories «Municipis de …» de la Viquipèdia.
   - Catalunya Nord: categories de la Viquipèdia (Rosselló, Conflent, Vallespir, Capcir, Alta Cerdanya, Fenolleda).
   - Illes Balears: cada illa fa de comarca (Mallorca, Menorca, Eivissa, Formentera).
   - L'Alguer: un sol municipi.
   Els municipis que no es poden assignar prenen la comarca del municipi veí més proper (l'script ho avisa).
3. Agrupa les comarques del Principat en les 8 vegueries.
4. Simplifica les geometries (~100 m) i les desa quantitzades en un JSON compacte.

Per generar-les:

```bash
pip install -r tools/requirements.txt
python tools/build_data.py --cache .cache-osm
```

O bé, a GitHub: **Actions → Dades → Run workflow**, que les genera i les desa al repositori.
Si el repositori no té `geo.json`, el flux de compilació les genera automàticament abans de compilar.

## Compilar

Cada `push` a `main` compila l'APK a GitHub Actions (**Actions → Compila l'APK → Artifacts**).
L'APK de release es signa amb la clau de depuració perquè es pugui instal·lar directament; per publicar-la,
configura-hi la teva clau.

Localment: Android Studio o `./gradlew assembleRelease` (JDK 17).

## Estructura

```
app/src/main/java/cat/paisoscatalans/
  data/        Geo (dades dels mapes), Estat (emmagatzematge local), Joc, Progrés i insígnies
  ui/mapa/     Mapa vectorial amb Canvas (sense biblioteques de mapes)
  ui/progres/  Progrés i detall de comarca
  ui/joc/      Joc diari
  ui/perfil/   Perfil i insígnies
  ui/compartir/ Full per compartir
  compartir/   Generació de la imatge i intents de destinació
tools/build_data.py   Generador de dades a partir d'OpenStreetMap
```
