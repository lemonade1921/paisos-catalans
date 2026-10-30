#!/usr/bin/env python3
"""
Genera un estat de demostració (llocs visitats, partides del joc, perfil) per fer les
captures de pantalla del README. Només el fa servir el flux «Captures de pantalla».

Ús: python3 tools/demo_estat.py > demo-estat.json
"""
import json
import os
import random
from datetime import date

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
geo = json.load(open(os.path.join(ROOT, "app", "src", "main", "assets", "geo.json"), encoding="utf-8"))

# Proporció de municipis visitats per comarca (la resta, res)
PROPORCIONS = {
    "Barcelonès": 1.0, "Baix Llobregat": 0.7, "Maresme": 0.8, "Vallès Occidental": 0.5, "Gironès": 0.6,
    "Alt Empordà": 0.45, "Baix Empordà": 0.55, "Ripollès": 0.4, "Garrotxa": 0.5, "Tarragonès": 0.7,
    "Osona": 0.3, "Cerdanya": 0.4, "Segrià": 0.2, "Priorat": 0.3, "Montsià": 0.3,
    "Andorra": 1.0, "Rosselló": 0.35, "Conflent": 0.2, "Matarranya": 0.4, "Llitera": 0.2,
    "València": 1.0, "Horta Nord": 0.5, "Safor": 0.5, "Marina Alta": 0.5, "Ribera Alta": 0.3,
    "Plana Alta": 0.3, "Alacantí": 0.5, "Mallorca": 0.35, "Menorca": 0.6, "Eivissa": 0.4,
    "l'Alguer": 1.0,
}

rnd = random.Random(1714)
avui = date.today().toordinal() - date(1970, 1, 1).toordinal()
def clau(nom):
    n = nom.strip().lower()
    for a in ("l'", "l’", "el ", "la ", "els ", "les "):
        if n.startswith(a):
            return n[len(a):].strip()
    return n


props = {clau(k): v for k, v in PROPORCIONS.items()}
comarca_nom = {c["id"]: clau(c["nom"]) for c in geo["comarques"]}
visitats = {}
for m in geo["municipis"]:
    p = props.get(comarca_nom.get(m["c"], ""), 0.0)
    if p and rnd.random() < p:
        visitats[m["id"]] = avui - rnd.randint(0, 900)

ids = [m["id"] for m in geo["municipis"]]
partides = {}
for d in range(1, 16):  # ratxa de 15 dies (sense el dia d'avui)
    n = rnd.randint(2, 5)
    partides[str(avui - d)] = {"intents": rnd.sample(ids, n), "encertada": True, "acabada": True, "pistes": 0}

print(json.dumps({
    "versio": 1, "nom": "Maria", "article": "la", "introduit": True, "tema": "sistema",
    "confeti": True, "recordatori": False, "horaRecordatori": 19 * 60,
    "visitats": visitats, "partides": partides,
}, ensure_ascii=False))
