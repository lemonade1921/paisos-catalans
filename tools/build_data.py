#!/usr/bin/env python3
"""
Genera app/src/main/assets/geo.json amb els límits de municipis, comarques,
vegueries i territoris dels Països Catalans a partir d'OpenStreetMap.

L'app no té permís d'Internet: aquestes dades s'empaqueten dins l'APK.
Aquest script només s'executa en temps de compilació (a GitHub Actions o al
teu ordinador) i és l'únic lloc on es fa servir la xarxa.

Fonts:
  - Geometries i noms: OpenStreetMap, extractes regionals de Geofabrik processats
    amb osmium-tool (© col·laboradors d'OSM, ODbL)
  - Assignació de comarques on OSM no les té (País Valencià si cal,
    Catalunya Nord): categories de la Viquipèdia en català
  - Vegueries: taula fixa (Llei 30/2010 i modificacions; 8 vegueries)

Ús (cal tenir `osmium` instal·lat: `apt install osmium-tool` o `pacman -S osmium-tool`):
    pip install -r tools/requirements.txt
    python tools/build_data.py            # descarrega i genera
    python tools/build_data.py --cache .cache-osm   # reutilitza descàrregues
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from datetime import date

from shapely.geometry import MultiPolygon, Point, Polygon, shape
from shapely.ops import unary_union

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "geo.json")
UA = "PaisosCatalansApp-databuilder/1.0 (https://github.com/lemonade1921/paisos-catalans)"

GEOFABRIK = "https://download.geofabrik.de/"

# Tolerància de simplificació (graus). ~0.001° ≈ 100 m: prou per a un mapa de límits.
TOL_MUNICIPI = 0.0010
TOL_COMARCA = 0.0015
TOL_GRAN = 0.0025
Q = 10000  # quantització: 1e-4 graus (~10 m)

# Municipis de predomini lingüístic castellà (Llei 4/1983 d'ús i ensenyament del valencià,
# art. 36), més els creats després a partir d'aquests (Pilar de la Horadada, Los Montesinos,
# San Isidro). Al País Valencià només es mostren els de predomini valencià.
PV_CASTELLANOPARLANTS = [
    # Alacant
    "Albatera", "Algorfa", "Almoradí", "Aspe", "Benejúzar", "Benferri", "Benijófar", "Bigastro",
    "Callosa de Segura", "Catral", "Cox", "Daya Nueva", "Daya Vieja", "Dolores", "Elda",
    "Formentera del Segura", "Granja de Rocamora", "Jacarilla", "Monforte del Cid", "Orihuela",
    "Rafal", "Redován", "Rojales", "Salinas", "San Fulgencio", "San Miguel de Salinas", "Sax",
    "Torrevieja", "Villena", "Pilar de la Horadada", "Los Montesinos", "San Isidro",
    # Castelló
    "Algimia de Almonacid", "Almedíjar", "Altura", "Arañuel", "Argelita", "Ayódar", "Azuébar",
    "Barracas", "Bejís", "Benafer", "Castellnovo", "Castillo de Villamalefa", "Caudiel", "Cirat",
    "Cortes de Arenoso", "Chóvar", "Espadilla", "Fanzara", "Fuente la Reina", "Fuentes de Ayódar",
    "Gaibiel", "Gátova", "Geldo", "Higueras", "Jérica", "Ludiente", "Matet", "Montán",
    "Montanejos", "Navajas", "Olocau del Rey", "Pavías", "Pina de Montalgrao", "Puebla de Arenoso",
    "Sacañet", "Segorbe", "Soneja", "Sot de Ferrer", "Teresa", "Toga", "Torás", "El Toro",
    "Torralba del Pinar", "Torrechiva", "Vall de Almonacid", "Vallat", "Villahermosa del Río",
    "Villamalur", "Villanueva de Viver", "Viver", "Zucaina",
    # València
    "Ademuz", "Alborache", "Alcublas", "Alpuente", "Andilla", "Anna", "Aras de los Olmos",
    "Aras de Alpuente", "Ayora", "Benagéber", "Bicorp", "Bolbaite", "Bugarra", "Buñol", "Calles",
    "Camporrobles", "Casas Altas", "Casas Bajas", "Castielfabib", "Caudete de las Fuentes",
    "Cofrentes", "Cortes de Pallás", "Chelva", "Chella", "Chera", "Cheste", "Chiva", "Chulilla",
    "Domeño", "Dos Aguas", "Enguera", "Fuenterrobles", "Gestalgar", "Godelleta", "Higueruelas",
    "Jalance", "Jarafuel", "Loriguilla", "Losa del Obispo", "Macastre", "Marines", "Millares",
    "Navarrés", "Pedralba", "Puebla de San Miguel", "Quesa", "Requena", "Siete Aguas", "Sinarcas",
    "Sot de Chera", "Teresa de Cofrentes", "Titaguas", "Torrebaja", "Tous", "Tuéjar", "Utiel",
    "Vallanca", "Venta del Moro", "Villar del Arzobispo", "Villargordo del Cabriel", "Yátova",
    "La Yesa", "Zarra",
]

# Franja de Ponent: (comarca, nom català, nom oficial castellà)
FRANJA = [
    ("Baix Cinca", "Fraga", "Fraga"), ("Baix Cinca", "Mequinensa", "Mequinenza"),
    ("Baix Cinca", "Saidí", "Zaidín"), ("Baix Cinca", "Torrent de Cinca", "Torrente de Cinca"),
    ("Baix Cinca", "Vilella de Cinca", "Velilla de Cinca"),
    ("Llitera", "Albelda", "Albelda"), ("Llitera", "Baells", "Baells"), ("Llitera", "el Campell", "Alcampell"),
    ("Llitera", "Camporrells", "Camporrells"), ("Llitera", "Castellonroi", "Castillonroy"),
    ("Llitera", "Peralta de la Sal", "Peralta de Calasanz"),
    ("Llitera", "Sant Esteve de Llitera", "San Esteban de Litera"), ("Llitera", "Sanui i Alins", "Azanuy-Alins"),
    ("Llitera", "Tamarit de Llitera", "Tamarite de Litera"), ("Llitera", "el Torricó", "Altorricón"),
    ("Llitera", "Valldellou", "Baldellou"), ("Llitera", "Vensilló", "Vencillón"),
    ("Matarranya", "Aiguaviva de Bergantes", "Aguaviva"), ("Matarranya", "Arenys de Lledó", "Arens de Lledó"),
    ("Matarranya", "Bellmunt de Mesquí", "Belmonte de San José"), ("Matarranya", "Beseit", "Beceite"),
    ("Matarranya", "Calaceit", "Calaceite"), ("Matarranya", "la Canyada de Beric", "La Cañada de Verich"),
    ("Matarranya", "la Codonyera", "La Codoñera"), ("Matarranya", "Cretes", "Cretas"),
    ("Matarranya", "Faió", "Fayón"), ("Matarranya", "Favara de Matarranya", "Fabara"),
    ("Matarranya", "Fondespatla", "Fuentespalda"), ("Matarranya", "Fórnols de Matarranya", "Fórnoles"),
    ("Matarranya", "la Freixneda", "La Fresneda"), ("Matarranya", "la Ginebrosa", "La Ginebrosa"),
    ("Matarranya", "Lledó d'Algars", "Lledó"), ("Matarranya", "Maella", "Maella"),
    ("Matarranya", "Massalió", "Mazaleón"), ("Matarranya", "Mont-roig de Tastavins", "Monroyo"),
    ("Matarranya", "Nonasp", "Nonaspe"), ("Matarranya", "Pena-roja", "Peñarroya de Tastavins"),
    ("Matarranya", "la Portellada", "La Portellada"), ("Matarranya", "Ràfels", "Ráfales"),
    ("Matarranya", "la Sorollera", "Valdealgorfa"), ("Matarranya", "Torredarques", "Torre de Arcas"),
    ("Matarranya", "la Torre del Comte", "Torre del Compte"), ("Matarranya", "la Torre de Vilella", "Torrevelilla"),
    ("Matarranya", "la Vall de Tormo", "Valdeltormo"), ("Matarranya", "Vall-de-roures", "Valderrobres"),
    ("Matarranya", "Valljunquera", "Valjunquera"),
    ("Ribagorça", "Areny de Noguera", "Arén"), ("Ribagorça", "Benavarri", "Benabarre"),
    ("Ribagorça", "Beranui", "Beranuy"), ("Ribagorça", "Bonansa", "Bonansa"),
    ("Ribagorça", "Castigaleu", "Castigaleu"), ("Ribagorça", "Estopanyà", "Estopiñán del Castillo"),
    ("Ribagorça", "Lasquarri", "Lascuarre"), ("Ribagorça", "Monesma i Queixigar", "Monesma y Cajigar"),
    ("Ribagorça", "Montanui", "Montanuy"), ("Ribagorça", "les Paüls", "Las Paúles"),
    ("Ribagorça", "la Pobla de Roda", "Isábena"), ("Ribagorça", "el Pont de Montanyana", "Puente de Montañana"),
    ("Ribagorça", "Sopeira", "Sopeira"), ("Ribagorça", "Tolba", "Tolva"),
    ("Ribagorça", "Viacamp", "Viacamp y Litera"),
]

# El Carxe (Múrcia): zona catalanoparlant sense municipi propi. Comprèn una part dels termes de
# Iecla, Jumella i Favanella i toca el Pinós i l'Alguenya. La calculem com la part d'aquests tres
# municipis que queda a menys de CARXE_DIST graus (~10 km) del Pinós i de l'Alguenya.
CARXE_MURCIA = ["Yecla", "Jumilla", "Abanilla"]
CARXE_VEINS = ["el Pinós", "Pinoso", "l'Alguenya", "Algueña"]
CARXE_DIST = 0.10
# Nuclis del Carxe: (nom en català segons l'AVL, noms possibles a OSM)
CARXE_NUCLIS = [
    ("el Carxe", ["El Carche", "Carche"]),
    ("el Raspai", ["Raspay", "El Raspay"]),
    ("la Torre del Rico", ["Torre del Rico", "La Torre del Rico"]),
    ("la Canyada del Trigo", ["Cañada del Trigo", "La Cañada del Trigo"]),
    ("la Raja", ["La Raja", "Raja"]),
    ("la Sarsa", ["La Zarza", "Zarza"]),
    ("l'Ombria de la Sarsa", ["Umbría de la Zarza", "La Umbría de la Zarza"]),
    ("la Canyada de l'Alenya", ["Cañada de la Leña", "La Cañada de la Leña", "Los Martínez", "Los Martines"]),
    ("el Collado dels Gabriels", ["Collado de los Gabrieles", "El Collado de los Gabrieles", "Los Gabrieles"]),
    ("l'Alberquilla", ["La Alberquilla", "Alberquilla", "La Arboleja"]),
    ("els Pinillos", ["Los Pinillos", "Pinillos"]),
    ("el Carrascalejo", ["Carrascalejo", "El Carrascalejo"]),
    ("els Escandells", ["Los Escandeles", "Escandeles", "Los Escandells"]),
    ("els Càpitos", ["Los Cápitos", "Cápitos"]),
]
# Polígon aproximat, només si no es pot calcular amb les dades d'OSM (lon, lat)
CARXE_RESERVA = [(-1.16, 38.30), (-1.10, 38.27), (-1.04, 38.30), (-1.03, 38.36), (-1.04, 38.43),
                 (-1.08, 38.48), (-1.14, 38.47), (-1.18, 38.40)]

TERRITORIS = [
    {
        "id": "PR",
        "nom": "Principat de Catalunya",
        "extractes": ["europe/spain/cataluna"],
        "limit": {"ISO3166-2": "ES-CT"},
        "codi": ("ine:municipio", ("08", "17", "25", "43")),
        "comarques": "osm",
        # La Vall d'Aran és occitanoparlant
        "exclou_comarques": ["Aran"],
    },
    {
        "id": "AD",
        "nom": "Andorra",
        "extractes": ["europe/andorra"],
        "limit": None,
        # Les 7 parròquies (admin_level=7 a OSM) fan de municipis; Andorra fa de comarca
        "nivell": "7",
        "codi": ("ISO3166-2", ("AD-",)),
        "comarques": "unica",
    },
    {
        "id": "CN",
        "nom": "Catalunya Nord",
        "extractes": ["europe/france/languedoc-roussillon", "europe/france/occitanie", "europe/france"],
        "limit": {"admin_level": "6", "ref:INSEE": "66"},
        "codi": ("ref:INSEE", ("66",)),
        "comarques": "viquipedia",
        # La Fenolleda és occitanoparlant
        "exclou_comarques": ["Fenolleda"],
    },
    {
        "id": "FR",
        "nom": "Franja de Ponent",
        "extractes": ["europe/spain/aragon"],
        "limit": None,
        "codi": ("ine:municipio", ("22", "44", "50")),
        "comarques": "llista",
        "llista": FRANJA,
    },
    {
        "id": "PV",
        "nom": "País Valencià",
        "extractes": ["europe/spain/valencia"],
        "limit": {"ISO3166-2": "ES-VC"},
        "codi": ("ine:municipio", ("03", "12", "46")),
        "comarques": "osm_o_viquipedia",
        "exclou_municipis": PV_CASTELLANOPARLANTS,
    },
    {
        "id": "IB",
        "nom": "Illes Balears",
        "extractes": ["europe/spain/islas-baleares"],
        "limit": {"ISO3166-2": "ES-IB"},
        "codi": ("ine:municipio", ("07",)),
        "comarques": "illes",
    },
    {
        "id": "AL",
        "nom": "l'Alguer",
        "extractes": ["europe/italy/isole", "europe/italy"],
        "limit": None,
        "municipi_nom": "Alghero",
        "comarques": "unica",
    },
]

# Comarques amb article, per construir els noms de categoria de la Viquipèdia.
COMARQUES_VIQUIPEDIA = {
    "PV": [
        "els Ports", "l'Alt Maestrat", "el Baix Maestrat", "l'Alcalatén", "la Plana Alta",
        "la Plana Baixa", "l'Alt Millars", "l'Alt Palància", "el Racó d'Ademús", "els Serrans",
        "el Camp de Túria", "el Camp de Morvedre", "l'Horta Nord", "l'Horta Oest", "València",
        "l'Horta Sud", "la Plana d'Utiel-Requena", "la Foia de Bunyol", "la Vall de Cofrents",
        "la Ribera Alta", "la Ribera Baixa", "la Canal de Navarrés", "la Costera",
        "la Vall d'Albaida", "la Safor", "l'Alcoià", "el Comtat", "la Marina Alta",
        "la Marina Baixa", "l'Alacantí", "l'Alt Vinalopó", "el Vinalopó Mitjà",
        "el Baix Vinalopó", "el Baix Segura",
    ],
    "CN": [
        "el Rosselló", "el Conflent", "el Vallespir", "el Capcir", "l'Alta Cerdanya", "la Fenolleda",
    ],
}

# Municipis que la Viquipèdia no permet assignar automàticament (nom català -> comarca)
COMARCA_MANUAL = {
    "CN": {
        "Castellnou": "Rosselló", "Corbera de les Cabanes": "Rosselló", "la Torre del Bisbe": "Rosselló",
        "Sançà": "Conflent", "els Banys d'Arles i Palaldà": "Vallespir", "Montalban lo Castèl": "Fenolleda",
    },
}

# Illes Balears: cada illa fa de "comarca" (lon_min, lat_min, lon_max, lat_max)
ILLES = [
    ("Mallorca", (2.20, 39.05, 3.55, 40.00)),
    ("Menorca", (3.75, 39.75, 4.40, 40.15)),
    ("Formentera", (1.35, 38.60, 1.62, 38.78)),
    ("Eivissa", (1.10, 38.78, 1.70, 39.15)),
]

VEGUERIES = {
    # L'Aran no forma part dels Països Catalans (és occitanoparlant)
    "Alt Pirineu": ["Alt Urgell", "Alta Ribagorça", "Cerdanya", "Pallars Jussà", "Pallars Sobirà"],
    "Barcelona": ["Baix Llobregat", "Barcelonès", "Maresme", "Vallès Occidental", "Vallès Oriental"],
    "Camp de Tarragona": ["Alt Camp", "Baix Camp", "Conca de Barberà", "Priorat", "Tarragonès"],
    "Catalunya Central": ["Bages", "Berguedà", "Moianès", "Osona", "Solsonès", "Lluçanès"],
    "Girona": ["Alt Empordà", "Baix Empordà", "Garrotxa", "Gironès", "Pla de l'Estany",
               "Ripollès", "Selva"],
    "Lleida": ["Garrigues", "Noguera", "Pla d'Urgell", "Segarra", "Segrià", "Urgell"],
    "Penedès": ["Alt Penedès", "Anoia", "Baix Penedès", "Garraf"],
    "Terres de l'Ebre": ["Baix Ebre", "Montsià", "Ribera d'Ebre", "Terra Alta"],
}

ARTICLES = ("el ", "la ", "els ", "les ", "l'", "l’")


# ---------------------------------------------------------------- utilitats

def log(*a):
    print(*a, file=sys.stderr, flush=True)


RESUM: list[str] = []


def resum(msg: str):
    """Línia del resum final (una sola anotació de tipus notice a GitHub Actions)."""
    log(msg)
    RESUM.append(msg)


def avis(msg: str):
    """Avís visible com a anotació a GitHub Actions."""
    log(msg)
    if os.environ.get("GITHUB_ACTIONS"):
        print(f"::warning::{msg}", flush=True)


def norm(s: str) -> str:
    s = s.replace("’", "'").strip().lower()
    s = re.sub(r"\s*\(.*?\)\s*", " ", s).strip()
    s = "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")
    for a in ARTICLES:
        if s.startswith(a):
            s = s[len(a):]
            break
    if re.search(r"\baran$", s):
        s = "aran"
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def slug(s: str) -> str:
    return norm(s).replace(" ", "-") or "x"


def sense_article(nom: str) -> str:
    n = nom.strip()
    for a in ARTICLES:
        if n.lower().startswith(a):
            r = n[len(a):]
            return r[:1].upper() + r[1:]
    return n


def nom_categoria(nom: str) -> str:
    """'el Rosselló' -> 'Municipis del Rosselló'"""
    n = nom.strip()
    low = n.lower()
    if low.startswith("els "):
        return "Municipis dels " + n[4:]
    if low.startswith("el "):
        return "Municipis del " + n[3:]
    if low.startswith("la "):
        return "Municipis de la " + n[3:]
    if low.startswith("l'"):
        return "Municipis de l'" + n[2:]
    return "Municipis de " + n


def http_get(url: str, data: bytes | None = None, timeout: int = 1000) -> bytes:
    req = urllib.request.Request(url, data=data, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def descarrega(url: str, desti: str):
    """Descarrega un fitxer gran a disc, amb reintents."""
    last = None
    for intent in range(5):
        try:
            log(f"  descarregant {url}")
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            tmp = desti + ".part"
            with urllib.request.urlopen(req, timeout=300) as r, open(tmp, "wb") as f:
                shutil.copyfileobj(r, f, 1 << 20)
            os.replace(tmp, desti)
            log(f"  {os.path.getsize(desti) // (1 << 20)} MB")
            return
        except urllib.error.HTTPError as e:
            if e.code == 404:
                raise
            last = e
            log(f"    error: {e}")
            time.sleep(20 * (intent + 1))
        except Exception as e:  # noqa: BLE001
            last = e
            log(f"    error: {e}")
            time.sleep(20 * (intent + 1))
    raise RuntimeError(f"No s'ha pogut descarregar {url}: {last}")


def limits_administratius(extractes: list[str], cache: str) -> list[tuple[dict, object]]:
    """
    Retorna (etiquetes, geometria) de totes les relacions boundary=administrative
    d'un extracte de Geofabrik (el primer de la llista que existeixi).
    osmium construeix els multipolígons.
    """
    sortida = None
    for extracte in extractes:
        nom = extracte.replace("/", "_")
        sortida = os.path.join(cache, nom + "-limits.geojsonseq")
        if os.path.exists(sortida):
            break
        pbf = os.path.join(cache, nom + ".osm.pbf")
        if not os.path.exists(pbf):
            try:
                descarrega(GEOFABRIK + extracte + "-latest.osm.pbf", pbf)
            except urllib.error.HTTPError:
                log(f"  {extracte} no existeix a Geofabrik, provo el següent")
                sortida = None
                continue
        filtrat = os.path.join(cache, nom + "-limits.osm.pbf")
        r = subprocess.run(["osmium", "tags-filter", "--overwrite", "-o", filtrat, pbf,
                            "r/boundary=administrative"], capture_output=True, text=True)
        if r.returncode != 0:
            raise RuntimeError(f"osmium tags-filter ({extracte}): {r.stderr.strip()[-400:]}")
        # Alguns límits que travessen la vora de l'extracte no es poden construir:
        # osmium ho avisa però continua; per això no exigim codi de sortida 0.
        r = subprocess.run(["osmium", "export", "--overwrite", "-f", "geojsonseq",
                            "--geometry-types=polygon", "--add-unique-id=type_id",
                            "-o", sortida, filtrat], capture_output=True, text=True)
        if r.returncode != 0:
            avis(f"osmium export ({extracte}) codi {r.returncode}: {r.stderr.strip()[-300:]}")
        if os.path.exists(sortida):
            break
    if not sortida or not os.path.exists(sortida):
        raise RuntimeError(f"No s'han pogut obtenir els límits de {extractes}")
    extracte = os.path.basename(sortida)
    out = []
    linies = 0
    mostra = None
    nivells: dict[str, int] = {}
    with open(sortida, encoding="utf-8") as f:
        for linia in f:
            linia = linia.strip().strip("\x1e").strip()
            if not linia:
                continue
            linies += 1
            try:
                feat = json.loads(linia)
            except json.JSONDecodeError:
                continue
            fid = str(feat.get("id", ""))
            props = feat.get("properties") or {}
            if mostra is None:
                mostra = (fid, sorted(props)[:12])
            if fid[:1] in ("n", "w"):
                continue  # vies tancades i nodes: només volem relacions
            nivell = str(props.get("admin_level", ""))
            nivells[nivell] = nivells.get(nivell, 0) + 1
            try:
                g = shape(feat["geometry"])
                if not g.is_valid:
                    g = g.buffer(0)
            except Exception:  # noqa: BLE001
                continue
            if g.is_empty:
                continue
            props["@id"] = fid.lstrip("ar") or str(len(out))
            out.append((props, g))
    log(f"{os.path.basename(sortida)}: {linies} línies, {len(out)} relacions; "
         f"nivells {dict(sorted(nivells.items()))}; mostra {mostra}")
    return out


def wiki_categoria(titol: str, cache_dir: str | None) -> list[str]:
    key = "wiki_" + slug(titol)
    if cache_dir:
        p = os.path.join(cache_dir, key + ".json")
        if os.path.exists(p):
            with open(p, encoding="utf-8") as f:
                return json.load(f)
    noms: list[str] = []
    cont = None
    while True:
        params = {
            "action": "query", "list": "categorymembers", "format": "json",
            "cmtitle": "Categoria:" + titol, "cmlimit": "500", "cmtype": "page", "cmnamespace": "0",
        }
        if cont:
            params["cmcontinue"] = cont
        url = "https://ca.wikipedia.org/w/api.php?" + urllib.parse.urlencode(params)
        try:
            data = json.loads(http_get(url, timeout=60))
        except Exception as e:  # noqa: BLE001
            log(f"    viquipèdia error {titol}: {e}")
            break
        noms += [m["title"] for m in data.get("query", {}).get("categorymembers", [])]
        cont = data.get("continue", {}).get("cmcontinue")
        if not cont:
            break
    if cache_dir:
        os.makedirs(cache_dir, exist_ok=True)
        with open(os.path.join(cache_dir, key + ".json"), "w", encoding="utf-8") as f:
            json.dump(noms, f)
    return noms


# ---------------------------------------------------------------- geometria

def nom_de(tags: dict) -> str:
    if tags.get("name:ca"):
        return tags["name:ca"].strip()
    n = tags.get("name", "").strip()
    if "/" in n:
        n = n.split("/")[0].strip()
    return n


def noms_candidats(tags: dict) -> set[str]:
    c = set()
    for k in ("name:ca", "name", "name:es", "name:fr", "official_name", "alt_name"):
        v = tags.get(k)
        if v:
            for part in v.split("/"):
                c.add(norm(part))
    return {x for x in c if x}


def poligons(g):
    if g is None or g.is_empty:
        return []
    if isinstance(g, Polygon):
        return [g]
    if isinstance(g, MultiPolygon):
        return list(g.geoms)
    return [p for p in getattr(g, "geoms", []) if isinstance(p, Polygon)]


def codifica(g, tol: float) -> list[list[int]]:
    """Anells com a llistes d'enters amb codificació delta: [x0,y0,dx1,dy1,...]"""
    s = g.simplify(tol, preserve_topology=True)
    anells = []
    for p in poligons(s):
        for ring in [p.exterior, *p.interiors]:
            coords = list(ring.coords)[:-1]
            if len(coords) < 3:
                continue
            out, px, py = [], None, None
            for x, y in coords:
                qx, qy = round(x * Q), round(y * Q)
                if px is None:
                    out += [qx, qy]
                elif qx != px or qy != py:
                    out += [qx - px, qy - py]
                else:
                    continue
                px, py = qx, qy
            if len(out) >= 6:
                anells.append(out)
    return anells


# ---------------------------------------------------------------- procés

def descarrega_territori(t: dict, cache: str):
    limits = limits_administratius(t["extractes"], cache)
    t["_limits"] = limits
    lim_geom = None
    if t.get("limit"):
        cands = [g for tags, g in limits if all(tags.get(k) == v for k, v in t["limit"].items())]
        if cands:
            lim_geom = unary_union(cands).buffer(0.001)
        else:
            avis(f"[{t['id']}] no trobo el límit {t['limit']} a l'extracte; faig servir només els codis oficials")
    clau_codi, prefixos = t.get("codi", (None, ()))

    municipis = []
    for tags, g in limits:
        if tags.get("admin_level") != t.get("nivell", "8"):
            continue
        if t.get("municipi_nom") and t["municipi_nom"] not in (tags.get("name"), tags.get("name:it")):
            continue
        rp = g.representative_point()
        codi = tags.get(clau_codi, "") if clau_codi else ""
        if clau_codi:
            # Els municipis oficials sempre porten codi (INE / INSEE); la resta són
            # altres entitats amb admin_level=8 que no compten.
            if not codi.startswith(prefixos):
                continue
        elif lim_geom is not None and not lim_geom.contains(rp):
            continue  # municipi veí, fora del territori
        municipis.append({
            "osm": tags["@id"], "nom": nom_de(tags), "cand": noms_candidats(tags),
            "geom": g, "lat": rp.y, "lon": rp.x, "comarca": None, "codi": codi,
        })
    t["_lim_geom"] = lim_geom

    if t.get("exclou_municipis"):
        exclou = {norm(n) for n in t["exclou_municipis"]}
        trobats = set()
        resta = []
        for m in municipis:
            coincidencia = m["cand"] & exclou
            if coincidencia:
                trobats |= coincidencia
            else:
                resta.append(m)
        no_trobats = sorted(exclou - trobats)
        resum(f"[{t['id']}] exclosos {len(municipis) - len(resta)} municipis de predomini castellà"
             + (f"; no trobats: {', '.join(no_trobats)}" if no_trobats else ""))
        municipis = resta

    if t.get("llista"):
        index = {}
        for comarca, nom_ca, nom_es in t["llista"]:
            for n in (nom_ca, nom_es):
                index[norm(n)] = (comarca, nom_ca)
        per_entrada: dict[str, list] = {}
        for m in municipis:
            hit = next((index[c] for c in m["cand"] if c in index), None)
            if hit:
                per_entrada.setdefault(norm(hit[1]), []).append((hit, m))
        resta = []
        trobats = set()
        for clau, cands in per_entrada.items():
            # Si un nom coincideix amb diversos municipis, preferim el que el porta com a name:ca
            cands.sort(key=lambda hm: (norm(hm[0][1]) not in {norm(hm[1]["nom"])}, -hm[1]["geom"].area))
            (comarca, nom_ca), m = cands[0]
            if len(cands) > 1:
                resum(f"[{t['id']}] {nom_ca}: {len(cands)} coincidències, triat {m['nom']}; descartats "
                      + ", ".join(x[1]["nom"] for x in cands[1:]))
            m["comarca"], m["nom"] = comarca, nom_ca
            trobats.add(clau)
            resta.append(m)
        no_trobats = sorted(nom_ca for _, nom_ca, _ in t["llista"] if norm(nom_ca) not in trobats)
        resum(f"[{t['id']}] {len(resta)} de {len(t['llista'])} municipis de la llista"
             + (f"; no trobats: {', '.join(no_trobats)}" if no_trobats else ""))
        municipis = resta
    # Un sol municipi per codi oficial (fora duplicats i entitats menors amb el mateix prefix)
    if clau_codi:
        per_codi: dict[str, dict] = {}
        sense = []
        for m in municipis:
            k = m.get("codi", "")[:5]
            if not k:
                sense.append(m)
            elif k not in per_codi or m["geom"].area > per_codi[k]["geom"].area:
                per_codi[k] = m
        if len(per_codi) + len(sense) != len(municipis):
            avis(f"[{t['id']}] {len(municipis) - len(per_codi) - len(sense)} duplicats eliminats per codi")
        municipis = list(per_codi.values()) + sense
    if clau_codi:
        llargs: dict[int, int] = {}
        for m in municipis:
            llargs[len(m.get("codi", ""))] = llargs.get(len(m.get("codi", "")), 0) + 1
        estranys = sorted(f"{m['nom']}({m.get('codi', '')})" for m in municipis if len(m.get("codi", "")) != 5)
        log(f"[{t['id']}] llargada dels codis {llargs}; codis no estàndard: {', '.join(estranys[:30])}")
    avis(f"[{t['id']}] {len(municipis)} municipis")
    return municipis


def comarques_osm(t: dict, municipis: list, cache: str) -> int:
    lim = t.get("_lim_geom")
    coms = [
        (sense_article(nom_de(tags)), g) for tags, g in t["_limits"]
        if tags.get("admin_level") == "7" and (lim is None or lim.contains(g.representative_point()))
    ]
    log(f"[{t['id']}] {len(coms)} comarques a OSM")
    if len(coms) < 3:
        return 0
    assignats = 0
    for m in municipis:
        pt = Point(m["lon"], m["lat"])
        for nom, g in coms:
            if g.contains(pt):
                m["comarca"] = nom
                assignats += 1
                break
    return assignats


def wiki_enllacos(titol: str, cache_dir: str | None) -> list[str]:
    """Articles enllaçats des d'una plantilla de navegació de la Viquipèdia."""
    key = "wikitpl_" + slug(titol)
    p = os.path.join(cache_dir, key + ".json") if cache_dir else None
    if p and os.path.exists(p):
        with open(p, encoding="utf-8") as f:
            return json.load(f)
    noms: list[str] = []
    cont = None
    while True:
        params = {
            "action": "query", "prop": "links", "format": "json", "titles": titol,
            "plnamespace": "0", "pllimit": "500", "redirects": "1",
        }
        if cont:
            params["plcontinue"] = cont
        url = "https://ca.wikipedia.org/w/api.php?" + urllib.parse.urlencode(params)
        try:
            data = json.loads(http_get(url, timeout=60))
        except Exception as e:  # noqa: BLE001
            avis(f"Viquipèdia: error amb {titol}: {e}")
            break
        for pag in data.get("query", {}).get("pages", {}).values():
            noms += [l["title"] for l in pag.get("links", [])]
        cont = data.get("continue", {}).get("plcontinue")
        if not cont:
            break
    if p:
        with open(p, "w", encoding="utf-8") as f:
            json.dump(noms, f)
    return noms


def comarques_viquipedia(t: dict, municipis: list, cache: str | None) -> int:
    index = {}
    for m in municipis:
        for c in m["cand"]:
            index.setdefault(c, []).append(m)
    assignats = 0
    resum = []
    for com in COMARQUES_VIQUIPEDIA[t["id"]]:
        base = nom_categoria(com)  # "Municipis del Rosselló"
        titols = set(wiki_categoria(base, cache))
        titols |= set(wiki_enllacos("Plantilla:" + base, cache))
        trobats = 0
        for titol in titols:
            for m in index.get(norm(titol), []):
                if m["comarca"] is None:
                    m["comarca"] = sense_article(com)
                    assignats += 1
                    trobats += 1
        resum.append(f"{sense_article(com)} {trobats}")
    avis(f"[{t['id']}] comarques via Viquipèdia: " + ", ".join(resum))
    return assignats


def omple_buits(municipis: list, per_defecte: str):
    """Municipis sense comarca: agafa la del municipi assignat més proper."""
    assignats = [m for m in municipis if m["comarca"]]
    buits = [m for m in municipis if not m["comarca"]]
    for m in buits:
        if assignats:
            k = math.cos(math.radians(m["lat"]))
            prox = min(assignats, key=lambda o: ((o["lon"] - m["lon"]) * k) ** 2 + (o["lat"] - m["lat"]) ** 2)
            m["comarca"] = prox["comarca"]
        else:
            m["comarca"] = per_defecte
    if buits:
        resum(f"{len(buits)} municipis assignats a comarca per proximitat: "
             + ", ".join(sorted(m["nom"] for m in buits)[:40]))


def norm_es(n: str) -> str:
    """Com norm(), però també treu els articles castellans."""
    n = norm(n)
    for art in ("los ", "las ", "el ", "la "):
        if n.startswith(art):
            n = n[len(art):]
    return n


def llocs_poblats(extractes: list[str], cache: str) -> list[tuple[str, object]]:
    """Nuclis de població (nodes place=*) d'un extracte: (nom, punt)."""
    nom = extractes[0].replace("/", "_")
    sortida = os.path.join(cache, nom + "-llocs.geojsonseq")
    if not os.path.exists(sortida):
        pbf = os.path.join(cache, nom + ".osm.pbf")
        if not os.path.exists(pbf):
            descarrega(GEOFABRIK + extractes[0] + "-latest.osm.pbf", pbf)
        filtrat = os.path.join(cache, nom + "-llocs.osm.pbf")
        subprocess.run(["osmium", "tags-filter", "--overwrite", "-o", filtrat, pbf,
                        "n/place=village,hamlet,isolated_dwelling,locality,neighbourhood"], check=True)
        subprocess.run(["osmium", "export", "--overwrite", "-f", "geojsonseq", "--geometry-types=point",
                        "-o", sortida, filtrat], check=True)
    out = []
    with open(sortida, encoding="utf-8") as f:
        for linia in f:
            linia = linia.strip().strip("\x1e").strip()
            if not linia:
                continue
            try:
                feat = json.loads(linia)
                props = feat.get("properties") or {}
                g = shape(feat["geometry"])
            except Exception:  # noqa: BLE001
                continue
            for k in ("name", "name:es", "name:ca", "alt_name"):
                if props.get(k):
                    out.append((props[k], g))
    return out


def calcula_carxe(t: dict, cache: str, valencians: list) -> list[dict]:
    """
    El Carxe: àrea que cobreix els seus nuclis i la franja fins a la ratlla del Pinós i
    l'Alguenya, retallada als termes de Iecla, Jumella i Favanella, i dividida en una
    zona per nucli (que fan de "municipis").
    """
    from shapely.geometry import MultiPoint
    from shapely.ops import voronoi_diagram
    try:
        limits = limits_administratius(t["extractes"], cache)
        noms = {norm(n) for n in CARXE_MURCIA}
        murcia = unary_union([g for tags, g in limits
                              if tags.get("admin_level") == "8" and noms_candidats(tags) & noms])
        veins_n = {norm(n) for n in CARXE_VEINS}
        veins = unary_union([m["geom"] for m in valencians if m["cand"] & veins_n])
        if murcia.is_empty or veins.is_empty:
            raise RuntimeError("no trobo Iecla/Jumella/Favanella o el Pinós/l'Alguenya")
        zona_busca = murcia.intersection(veins.buffer(0.22))  # on poden ser els nuclis

        cercats = {norm_es(alt): nom_ca for nom_ca, alts in CARXE_NUCLIS for alt in alts}
        trobats: dict[str, object] = {}
        for nom, pt in llocs_poblats(t["extractes"], cache):
            nom_ca = cercats.get(norm_es(nom))
            if nom_ca and nom_ca not in trobats and zona_busca.contains(pt):
                trobats[nom_ca] = pt
        no = [n for n, _ in CARXE_NUCLIS if n not in trobats]
        resum(f"[CX] nuclis trobats: {', '.join(trobats)}" + (f"; no trobats: {', '.join(no)}" if no else ""))
        if len(trobats) < 3:
            raise RuntimeError(f"només {len(trobats)} nuclis")

        # Ratlla amb el Pinós i l'Alguenya
        ratlla = murcia.boundary.intersection(veins.buffer(0.003))
        punts = list(trobats.values())
        nucli = MultiPoint(punts).convex_hull
        area = unary_union([nucli, ratlla]).convex_hull.buffer(0.025)
        area = unary_union(poligons(area.intersection(murcia))).buffer(0)
        if area.is_empty:
            raise RuntimeError("àrea buida")
        resum(f"[CX] àrea del Carxe ~{area.area * 9000:.0f} km²")

        celles = voronoi_diagram(MultiPoint(punts), envelope=area.envelope.buffer(0.1))
        municipis = []
        for nom_ca, pt in trobats.items():
            cel = next((c for c in celles.geoms if c.contains(pt)), None)
            if cel is None:
                continue
            g = unary_union(poligons(cel.intersection(area))).buffer(0)
            if g.is_empty:
                continue
            municipis.append({"osm": "carxe-" + slug(nom_ca), "nom": nom_ca, "cand": {norm(nom_ca)},
                              "geom": g, "lat": pt.y, "lon": pt.x, "comarca": "Carxe"})
        return municipis
    except Exception as e:  # noqa: BLE001
        resum(f"[CX] no s'ha pogut calcular el Carxe ({e}); faig servir el polígon de reserva")
        g = Polygon(CARXE_RESERVA)
        rp = g.representative_point()
        return [{"osm": "carxe", "nom": "el Carxe", "cand": set(), "geom": g,
                 "lat": rp.y, "lon": rp.x, "comarca": "Carxe"}]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=os.path.join(ROOT, ".cache-osm"),
                    help="directori on es desen els extractes descarregats")
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()
    os.makedirs(args.cache, exist_ok=True)
    if shutil.which("osmium") is None:
        sys.exit("Cal instal·lar osmium-tool (apt install osmium-tool)")

    veg_de = {norm(c): v for v, cs in VEGUERIES.items() for c in cs}

    sortida = {
        "versio": date.today().isoformat(),
        "atribucio": "Límits: © col·laboradors d'OpenStreetMap (ODbL)",
        "territoris": [], "vegueries": [], "comarques": [], "municipis": [],
    }
    geoms_veg: dict[str, list] = {}
    per_territori: dict[str, list] = {}

    for t in TERRITORIS:
        mode = t["comarques"]
        if mode == "carxe":
            municipis = calcula_carxe(t, args.cache, per_territori.get("PV", []))
        else:
            municipis = descarrega_territori(t, args.cache)
        per_territori[t["id"]] = municipis
        if mode == "osm":
            comarques_osm(t, municipis, args.cache)
        elif mode == "osm_o_viquipedia":
            if comarques_osm(t, municipis, args.cache) < len(municipis) * 0.8:
                for m in municipis:
                    m["comarca"] = None
                comarques_viquipedia(t, municipis, args.cache)
        elif mode == "viquipedia":
            comarques_viquipedia(t, municipis, args.cache)
        elif mode == "illes":
            for m in municipis:
                for nom, (x0, y0, x1, y1) in ILLES:
                    if x0 <= m["lon"] <= x1 and y0 <= m["lat"] <= y1:
                        m["comarca"] = nom
                        break
        elif mode == "unica":
            for m in municipis:
                m["comarca"] = t["nom"]
        for nom_m, com in COMARCA_MANUAL.get(t["id"], {}).items():
            for m in municipis:
                if norm(nom_m) in m["cand"] or norm(nom_m) == norm(m["nom"]):
                    m["comarca"] = com
        omple_buits(municipis, t["nom"])

        if t.get("exclou_comarques"):
            fora = {norm(c) for c in t["exclou_comarques"]}
            abans = len(municipis)
            municipis = [m for m in municipis if norm(m["comarca"]) not in fora]
            resum(f"[{t['id']}] exclosos {abans - len(municipis)} municipis de: {', '.join(t['exclou_comarques'])}")
        resum(f"[{t['id']}] total final: {len(municipis)} municipis")

        per_comarca: dict[str, list] = {}
        for m in municipis:
            per_comarca.setdefault(m["comarca"], []).append(m)

        tot_territori = []
        for nom_c, ms in sorted(per_comarca.items(), key=lambda kv: norm(kv[0])):
            cid = f"{t['id']}-{slug(nom_c)}"
            veg = veg_de.get(norm(nom_c)) if t["id"] == "PR" else None
            if t["id"] == "PR" and veg is None:
                avis(f"Comarca sense vegueria: {nom_c}")
            vid = f"VEG-{slug(veg)}" if veg else None
            g = unary_union([m["geom"] for m in ms])
            tot_territori.append(g)
            if vid:
                geoms_veg.setdefault(vid, []).append(g)
                if not any(v["id"] == vid for v in sortida["vegueries"]):
                    sortida["vegueries"].append({"id": vid, "nom": veg, "t": t["id"]})
            sortida["comarques"].append({
                "id": cid, "nom": nom_c, "t": t["id"], "v": vid, "r": codifica(g, TOL_COMARCA),
            })
            for m in sorted(ms, key=lambda m: norm(m["nom"])):
                sortida["municipis"].append({
                    "id": f"r{m['osm']}", "nom": m["nom"], "c": cid,
                    "lat": round(m["lat"], 5), "lon": round(m["lon"], 5),
                    "r": codifica(m["geom"], TOL_MUNICIPI),
                })
        sortida["territoris"].append({
            "id": t["id"], "nom": t["nom"], "r": codifica(unary_union(tot_territori), TOL_GRAN),
        })
        log(f"[{t['id']}] {len(per_comarca)} comarques")

    for v in sortida["vegueries"]:
        v["r"] = codifica(unary_union(geoms_veg[v["id"]]), TOL_GRAN)
    sortida["vegueries"].sort(key=lambda v: norm(v["nom"]))

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(sortida, f, ensure_ascii=False, separators=(",", ":"))
    resum(f"TOTAL {len(sortida['municipis'])} municipis, {len(sortida['comarques'])} comarques, "
          f"{len(sortida['vegueries'])} vegueries")
    if os.environ.get("GITHUB_ACTIONS"):
        text = " %0A".join(m.replace("%", "%25") for m in RESUM)
        print(f"::notice title=Resum de dades::{text}", flush=True)
    log(f"Escrit {args.out}: {len(sortida['municipis'])} municipis, "
        f"{len(sortida['comarques'])} comarques, {len(sortida['vegueries'])} vegueries, "
        f"{os.path.getsize(args.out) // 1024} KB")
    if len(sortida["municipis"]) < 1400:
        raise RuntimeError(f"Massa pocs municipis ({len(sortida['municipis'])}); alguna descàrrega ha fallat")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001
        import traceback
        traceback.print_exc()
        if os.environ.get("GITHUB_ACTIONS"):
            tb = traceback.extract_tb(e.__traceback__)[-1]
            print(f"::error::{type(e).__name__} a la línia {tb.lineno} ({tb.name}): {e}", flush=True)
        sys.exit(1)
