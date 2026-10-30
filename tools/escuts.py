#!/usr/bin/env python3
"""
Descarrega els escuts dels territoris des de la Viquipèdia / Wikimedia Commons.

Per a cada territori es busca l'article de la Viquipèdia en català, se'n mira l'element
de Wikidata i la propietat P94 («imatge de l'escut d'armes»), i es baixa una versió PNG
del fitxer de Commons juntament amb l'autoria i la llicència. Res no es dibuixa a mà.

Sortida: app/src/main/assets/escuts/<ID>.png i escuts.json (fitxer, llicència, autor).
L'app no es connecta mai: només aquest script, en temps de compilació, fa servir la xarxa.
"""
from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "escuts")
UA = "PaisosCatalansApp-databuilder/1.0 (https://github.com/lemonade1921/paisos-catalans)"

# Territori -> articles de la Viquipèdia a provar per ordre (el primer amb escut guanya)
TERRITORIS = {
    "PR": ["Catalunya"],
    "AD": ["Andorra"],
    "CN": ["Catalunya del Nord", "Comtat de Rosselló", "Rosselló (comarca)"],
    "FR": ["Franja de Ponent"],
    "PV": ["País Valencià", "Comunitat Valenciana", "Regne de València"],
    "IB": ["Illes Balears"],
    "AL": ["l'Alguer", "L'Alguer"],
}


def get_json(url: str) -> dict:
    for intent in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read())
        except Exception as e:  # noqa: BLE001
            print(f"  error {e}; reintent", file=sys.stderr)
            time.sleep(5 * (intent + 1))
    raise RuntimeError(f"no s'ha pogut obtenir {url}")


def fitxer_escut(titol: str) -> str | None:
    q = urllib.parse.urlencode({
        "action": "wbgetentities", "sites": "cawiki", "titles": titol, "props": "claims",
        "format": "json", "redirects": "yes",
    })
    d = get_json("https://www.wikidata.org/w/api.php?" + q)
    for ent in d.get("entities", {}).values():
        for c in ent.get("claims", {}).get("P94", []):
            v = c.get("mainsnak", {}).get("datavalue", {}).get("value")
            if v:
                return v
    return None


def neteja(html: str) -> str:
    return re.sub(r"<[^>]+>", "", html or "").strip()


def descarrega(fitxer: str, desti: str) -> dict:
    q = urllib.parse.urlencode({
        "action": "query", "titles": "File:" + fitxer, "prop": "imageinfo",
        "iiprop": "url|extmetadata", "iiurlwidth": "320", "format": "json",
    })
    d = get_json("https://commons.wikimedia.org/w/api.php?" + q)
    pag = next(iter(d["query"]["pages"].values()))
    info = pag["imageinfo"][0]
    url = info.get("thumburl") or info["url"]
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r, open(desti, "wb") as f:
        f.write(r.read())
    meta = info.get("extmetadata", {})
    return {
        "fitxer": fitxer,
        "llicencia": neteja(meta.get("LicenseShortName", {}).get("value", "")),
        "autor": neteja(meta.get("Artist", {}).get("value", ""))[:120],
        "pagina": info.get("descriptionurl", ""),
    }


def main():
    os.makedirs(OUT, exist_ok=True)
    resultat = {}
    resum = []
    for tid, titols in TERRITORIS.items():
        fet = False
        for titol in titols:
            try:
                f = fitxer_escut(titol)
            except Exception as e:  # noqa: BLE001
                print(f"[{tid}] {titol}: {e}", file=sys.stderr)
                continue
            if not f:
                continue
            try:
                resultat[tid] = descarrega(f, os.path.join(OUT, f"{tid}.png")) | {"article": titol}
                resum.append(f"{tid}: {f} ({resultat[tid]['llicencia']})")
                fet = True
                break
            except Exception as e:  # noqa: BLE001
                print(f"[{tid}] {f}: {e}", file=sys.stderr)
        if not fet:
            resum.append(f"{tid}: sense escut a Wikidata")
    with open(os.path.join(OUT, "escuts.json"), "w", encoding="utf-8") as fh:
        json.dump(resultat, fh, ensure_ascii=False, indent=1)
    print("\n".join(resum))
    if os.environ.get("GITHUB_ACTIONS"):
        print("::notice title=Escuts::" + " %0A".join(r.replace("%", "%25") for r in resum))


if __name__ == "__main__":
    main()
