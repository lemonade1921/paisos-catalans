#!/usr/bin/env python3
"""
Genera app/src/main/assets/geo.json amb els límits de municipis, comarques,
vegueries i territoris dels Països Catalans a partir d'OpenStreetMap.

L'app no té permís d'Internet: aquestes dades s'empaqueten dins l'APK.
Aquest script només s'executa en temps de compilació (a GitHub Actions o al
teu ordinador) i és l'únic lloc on es fa servir la xarxa.

Fonts:
  - Geometries i noms: OpenStreetMap via Overpass API (© col·laboradors d'OSM, ODbL)
  - Assignació de comarques on OSM no les té (País Valencià si cal,
    Catalunya Nord): categories de la Viquipèdia en català
  - Vegueries: taula fixa (Llei 30/2010 i modificacions; 8 vegueries)

Ús:
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
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from datetime import date

from shapely.geometry import LineString, MultiPolygon, Point, Polygon
from shapely.ops import polygonize, unary_union

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "geo.json")
UA = "PaisosCatalansApp-databuilder/1.0 (https://github.com/lemonade1921/paisos-catalans)"

OVERPASS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]

# Tolerància de simplificació (graus). ~0.001° ≈ 100 m: prou per a un mapa de límits.
TOL_MUNICIPI = 0.0010
TOL_COMARCA = 0.0015
TOL_GRAN = 0.0025
Q = 10000  # quantització: 1e-4 graus (~10 m)

TERRITORIS = [
    {
        "id": "PR",
        "nom": "Principat de Catalunya",
        "area": '["boundary"="administrative"]["ISO3166-2"="ES-CT"]',
        "comarques": "osm",
    },
    {
        "id": "PV",
        "nom": "País Valencià",
        "area": '["boundary"="administrative"]["ISO3166-2"="ES-VC"]',
        "comarques": "osm_o_viquipedia",
    },
    {
        "id": "IB",
        "nom": "Illes Balears",
        "area": '["boundary"="administrative"]["ISO3166-2"="ES-IB"]',
        "comarques": "illes",
    },
    {
        "id": "CN",
        "nom": "Catalunya Nord",
        "area": '["boundary"="administrative"]["admin_level"="6"]["ref:INSEE"="66"]',
        "comarques": "viquipedia",
    },
    {
        "id": "AL",
        "nom": "l'Alguer",
        "area": '["boundary"="administrative"]["ISO3166-2"="IT-88"]',
        "municipi_filtre": '["name"="Alghero"]',
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

# Illes Balears: cada illa fa de "comarca" (lon_min, lat_min, lon_max, lat_max)
ILLES = [
    ("Mallorca", (2.20, 39.05, 3.55, 40.00)),
    ("Menorca", (3.75, 39.75, 4.40, 40.15)),
    ("Formentera", (1.35, 38.60, 1.62, 38.78)),
    ("Eivissa", (1.10, 38.78, 1.70, 39.15)),
]

VEGUERIES = {
    "Alt Pirineu i Aran": ["Alt Urgell", "Alta Ribagorça", "Cerdanya", "Pallars Jussà",
                           "Pallars Sobirà", "Aran"],
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


def norm(s: str) -> str:
    s = s.replace("’", "'").strip().lower()
    s = re.sub(r"\s*\(.*?\)\s*", " ", s).strip()
    s = "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")
    for a in ARTICLES:
        if s.startswith(a):
            s = s[len(a):]
            break
    if "val d'aran" in s or s == "era val d'aran":
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


def overpass(query: str, cache_dir: str | None, key: str) -> dict:
    if cache_dir:
        p = os.path.join(cache_dir, key + ".json")
        if os.path.exists(p):
            with open(p, encoding="utf-8") as f:
                return json.load(f)
    body = urllib.parse.urlencode({"data": query}).encode()
    last = None
    for intent in range(4):
        for url in OVERPASS:
            try:
                log(f"  overpass {key} -> {url}")
                raw = http_get(url, body)
                data = json.loads(raw)
                if cache_dir:
                    os.makedirs(cache_dir, exist_ok=True)
                    with open(os.path.join(cache_dir, key + ".json"), "w", encoding="utf-8") as f:
                        json.dump(data, f)
                return data
            except Exception as e:  # noqa: BLE001
                last = e
                log(f"    error: {e}")
        time.sleep(30 * (intent + 1))
    raise RuntimeError(f"Overpass ha fallat per {key}: {last}")


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

def rel_geom(el: dict):
    outers, inners = [], []
    for m in el.get("members", []):
        if m.get("type") != "way" or "geometry" not in m:
            continue
        pts = [(p["lon"], p["lat"]) for p in m["geometry"] if p]
        if len(pts) < 2:
            continue
        (inners if m.get("role") == "inner" else outers).append(LineString(pts))
    if not outers:
        return None
    polys = list(polygonize(unary_union(outers)))
    if not polys:
        return None
    g = unary_union(polys)
    if inners:
        holes = list(polygonize(unary_union(inners)))
        if holes:
            g = g.difference(unary_union(holes))
    if not g.is_valid:
        g = g.buffer(0)
    return g if not g.is_empty else None


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

def descarrega_territori(t: dict, cache: str | None):
    area = f'area{t["area"]}->.a;'
    log(f"[{t['id']}] límit del territori")
    lim = overpass(f'[out:json][timeout:900];rel{t["area"]};out geom;', cache, f"{t['id']}_limit")
    lim_geom = unary_union([g for g in (rel_geom(e) for e in lim["elements"]) if g is not None])

    log(f"[{t['id']}] municipis")
    filtre = t.get("municipi_filtre", "")
    q = (f'[out:json][timeout:900][maxsize:2000000000];{area}'
         f'rel(area.a)["boundary"="administrative"]["admin_level"="8"]{filtre};out geom;')
    data = overpass(q, cache, f"{t['id']}_municipis")
    municipis = []
    for e in data["elements"]:
        if e.get("type") != "relation":
            continue
        g = rel_geom(e)
        if g is None:
            continue
        rp = g.representative_point()
        if not t.get("municipi_filtre") and not lim_geom.buffer(0.001).contains(rp):
            continue  # municipi veí que toca la frontera
        tags = e.get("tags", {})
        municipis.append({
            "osm": e["id"], "nom": nom_de(tags), "cand": noms_candidats(tags),
            "geom": g, "lat": rp.y, "lon": rp.x, "comarca": None,
        })
    log(f"[{t['id']}] {len(municipis)} municipis")
    return municipis


def comarques_osm(t: dict, municipis: list, cache: str | None) -> int:
    area = f'area{t["area"]}->.a;'
    q = (f'[out:json][timeout:900];{area}'
         f'rel(area.a)["boundary"~"^(administrative|political)$"]["admin_level"="7"];out geom;')
    data = overpass(q, cache, f"{t['id']}_comarques")
    coms = []
    for e in data["elements"]:
        if e.get("type") != "relation":
            continue
        g = rel_geom(e)
        if g is not None:
            coms.append((sense_article(nom_de(e.get("tags", {}))), g))
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


def comarques_viquipedia(t: dict, municipis: list, cache: str | None) -> int:
    index = {}
    for m in municipis:
        for c in m["cand"]:
            index.setdefault(c, []).append(m)
    assignats = 0
    for com in COMARQUES_VIQUIPEDIA[t["id"]]:
        noms = wiki_categoria(nom_categoria(com), cache)
        trobats = 0
        for titol in noms:
            for m in index.get(norm(titol), []):
                if m["comarca"] is None:
                    m["comarca"] = sense_article(com)
                    assignats += 1
                    trobats += 1
        log(f"    {com}: {trobats}/{len(noms)}")
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
        log(f"    {len(buits)} municipis assignats per proximitat: "
            + ", ".join(sorted(m["nom"] for m in buits)[:40]))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=None, help="directori per desar les respostes descarregades")
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()

    veg_de = {norm(c): v for v, cs in VEGUERIES.items() for c in cs}

    sortida = {
        "versio": date.today().isoformat(),
        "atribucio": "Límits: © col·laboradors d'OpenStreetMap (ODbL)",
        "territoris": [], "vegueries": [], "comarques": [], "municipis": [],
    }
    geoms_veg: dict[str, list] = {}

    for t in TERRITORIS:
        municipis = descarrega_territori(t, args.cache)
        mode = t["comarques"]
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
        omple_buits(municipis, t["nom"])

        per_comarca: dict[str, list] = {}
        for m in municipis:
            per_comarca.setdefault(m["comarca"], []).append(m)

        tot_territori = []
        for nom_c, ms in sorted(per_comarca.items(), key=lambda kv: norm(kv[0])):
            cid = f"{t['id']}-{slug(nom_c)}"
            veg = veg_de.get(norm(nom_c)) if t["id"] == "PR" else None
            if t["id"] == "PR" and veg is None:
                log(f"    AVÍS: comarca sense vegueria: {nom_c}")
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
    log(f"Escrit {args.out}: {len(sortida['municipis'])} municipis, "
        f"{len(sortida['comarques'])} comarques, {len(sortida['vegueries'])} vegueries, "
        f"{os.path.getsize(args.out) // 1024} KB")
    if len(sortida["municipis"]) < 1500:
        log("ERROR: massa pocs municipis, alguna descàrrega ha fallat")
        sys.exit(1)


if __name__ == "__main__":
    main()
