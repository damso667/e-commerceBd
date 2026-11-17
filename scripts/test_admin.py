"""Test de bout en bout du back-office.

Usage : serveur demarre, puis  python scripts/test_admin.py
"""
import json, time, urllib.error, urllib.request, http.cookiejar, uuid

API = "http://localhost:8080"
resultats = []


def session():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def call(op, method, path, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with op.open(req) as r:
            txt = r.read().decode()
            return r.status, (json.loads(txt) if txt.strip() else None)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, json.loads(txt)
        except Exception:
            return e.code, txt


def verifier(libelle, condition, detail=""):
    resultats.append(bool(condition))
    print(f"  [{'OK  ' if condition else 'ECHEC'}] {libelle}" + (f"  -> {detail}" if detail else ""))


def titre(t):
    print(f"\n--- {t} ---")


def msg(b):
    return b.get("message") if isinstance(b, dict) else str(b)[:120]


# ==================================================================
admin = session()
client = session()

titre("0. Connexions")
s, b = call(admin, "POST", "/api/auth/login", {"email": "ralph@gmail.com", "password": "password"})
verifier("login ADMIN", s == 200 and b.get("role") == "ROLE_ADMIN", f"HTTP {s}")
s, b = call(client, "POST", "/api/auth/login", {"email": "adrien@gmail.com", "password": "password"})
verifier("login USER", s == 200, f"HTTP {s}")

# ------------------------------------------------------------------
titre("1. Tableau de bord : periode par defaut (30 jours)")
s, tdb = call(admin, "GET", "/api/admin/tableau-de-bord")
verifier("200", s == 200, f"HTTP {s}")
verifier("periode de 30 jours", tdb["periode"]["nombreJours"] == 30,
         f"{tdb['periode']['debut']} -> {tdb['periode']['fin']}")
verifier("serie complete (un point par jour)", len(tdb["serie"]) == 30, f"{len(tdb['serie'])} points")
attendus = ["periode", "ventes", "aujourdhui", "serie", "commandesParStatut", "paiements",
            "meilleursArticles", "meilleursClients", "caParCategorie", "clients", "alertes"]
manquants = [k for k in attendus if k not in tdb]
verifier("toutes les sections presentes", not manquants, f"manquants : {manquants}")

titre("2. Indicateurs de vente")
v = tdb["ventes"]
verifier("chiffre d'affaires present", v["chiffreAffaires"] is not None, str(v["chiffreAffaires"]))
verifier("panier moyen coherent",
         (v["nombreCommandesPayees"] == 0 and float(v["panierMoyen"]) == 0)
         or abs(float(v["panierMoyen"]) * v["nombreCommandesPayees"] - float(v["chiffreAffaires"])) < 1,
         f"CA={v['chiffreAffaires']} / {v['nombreCommandesPayees']} cmd = {v['panierMoyen']}")
verifier("taux de conversion en %", 0 <= float(v["tauxConversion"]) <= 100,
         f"{v['tauxConversion']}%")
print(f"       aujourd'hui : CA={tdb['aujourdhui']['chiffreAffaires']} "
      f"commandes={tdb['aujourdhui']['nombreCommandes']} "
      f"nouveaux clients={tdb['aujourdhui']['nouveauxClients']}")

titre("3. Sante des paiements par operateur")
p = tdb["paiements"]
verifier("taux de reussite global calcule", p["tauxReussiteGlobal"] is not None,
         f"{p['tauxReussiteGlobal']}%")
for f in p["parFournisseur"]:
    print(f"       {f['libelle']:20} total={f['total']:3} reussis={f['reussis']:3} "
          f"echoues={f['echoues']:3} expires={f['expires']:3} "
          f"taux={f['tauxReussite']}% encaisse={f['montantEncaisse']}")
verifier("au moins un operateur mesure", len(p["parFournisseur"]) >= 1,
         f"{len(p['parFournisseur'])} operateur(s)")
print(f"       motifs d'echec : {p['motifsEchec']}")

titre("4. Alertes")
a = tdb["alertes"]
print(f"       a preparer={a['commandesAPreparer']} paiements en cours={a['paiementsEnCours']} "
      f"bloques={a['paiementsBloques']} ruptures={a['articlesEnRupture']} "
      f"stock faible={a['articlesStockFaible']} (seuil {a['seuilStockFaible']})")
verifier("seuil de stock faible configure", a["seuilStockFaible"] > 0, str(a["seuilStockFaible"]))
verifier("detail du stock faible fourni", isinstance(a["stockFaible"], list),
         f"{len(a['stockFaible'])} article(s)")

titre("5. Periode explicite et bornes")
s, b = call(admin, "GET", "/api/admin/tableau-de-bord?debut=2026-11-01&fin=2026-11-17")
verifier("periode explicite acceptee", s == 200 and b["periode"]["nombreJours"] == 17,
         f"HTTP {s} / {b['periode']['nombreJours'] if s == 200 else ''} jours")
s, b = call(admin, "GET", "/api/admin/tableau-de-bord?debut=2026-11-20&fin=2026-11-01")
verifier("debut > fin -> 400", s == 400, f"HTTP {s} : {msg(b)}")
s, b = call(admin, "GET", "/api/admin/tableau-de-bord?debut=2020-01-01&fin=2026-11-17")
verifier("periode trop longue -> 400", s == 400, f"HTTP {s} : {msg(b)}")

# ------------------------------------------------------------------
titre("6. Pagination des commandes")
s, page = call(admin, "GET", "/api/admin/commandes?page=0&size=2&sort=dateCreation,desc")
verifier("200", s == 200, f"HTTP {s}")
verifier("taille de page respectee", len(page["contenu"]) <= 2, f"{len(page['contenu'])} element(s)")
verifier("total renseigne", page["totalElements"] >= len(page["contenu"]),
         f"total={page['totalElements']} pages={page['totalPages']}")
verifier("liste sans les lignes de commande (vue allegee)",
         "lignes" not in (page["contenu"][0] if page["contenu"] else {}),
         "nombreLignes expose a la place")
if page["contenu"]:
    c0 = page["contenu"][0]
    print(f"       exemple : commande {c0['idCommande']} {c0['statut']} "
          f"{c0['montantTotal']} {c0['nomClient']} ({c0['nombreLignes']} ligne(s))")

s, b = call(admin, "GET", "/api/admin/commandes?size=100000")
verifier("size abusif plafonne", s == 200 and b["taille"] <= 200, f"taille={b.get('taille')}")

titre("7. Filtres et recherche sur les commandes")
s, b = call(admin, "GET", "/api/admin/commandes?statut=ANNULEE")
verifier("filtre par statut", s == 200 and all(c["statut"] == "ANNULEE" for c in b["contenu"]),
         f"{b['totalElements']} annulee(s)")
s, b = call(admin, "GET", "/api/admin/commandes?recherche=adrien@gmail.com")
verifier("recherche par email client", s == 200 and b["totalElements"] > 0,
         f"{b['totalElements']} resultat(s)")
s, b = call(admin, "GET", "/api/admin/commandes?recherche=zzzintrouvablezzz")
verifier("recherche sans resultat -> page vide, pas d'erreur",
         s == 200 and b["totalElements"] == 0, f"HTTP {s}")

# ------------------------------------------------------------------
titre("8. Un paiement reussi, pour pouvoir le rembourser")
call(client, "DELETE", "/api/user/viderPanier")
call(client, "POST", "/api/user/ajoutPanier", {"idArticle": 2, "quantite": 2})
s, b = call(client, "POST", "/api/user/commandes",
            {"fournisseur": "MTN_MOMO", "numeroPayeur": "677123456"},
            {"Idempotency-Key": str(uuid.uuid4())})
verifier("commande creee", s == 201, f"HTTP {s} : {msg(b)}")
ref_payee = b["paiement"]["reference"]
cmd_payee = b["commande"]["idCommande"]
montant_payee = b["commande"]["montantTotal"]
for _ in range(12):
    s, st = call(client, "GET", f"/api/user/paiements/{ref_payee}")
    if st.get("terminal"):
        break
    time.sleep(2)
verifier("paiement PAYE", st["statut"] == "PAYE", st["statut"])

titre("9. Recherche de paiements cote admin")
s, b = call(admin, "GET", "/api/admin/paiements?page=0&size=5")
verifier("liste paginee", s == 200 and "contenu" in b,
         f"HTTP {s} total={b.get('totalElements')}")
verifier("numero payeur en clair pour le support",
         any(p.get("numeroPayeur") and not p["numeroPayeur"].startswith("*") for p in b["contenu"]),
         "nécessaire pour un remboursement manuel")
s, b = call(admin, "GET", "/api/admin/paiements?statut=ECHOUE")
verifier("filtre par statut", s == 200 and all(p["statut"] == "ECHOUE" for p in b["contenu"]),
         f"{b['totalElements']} echec(s)")
s, b = call(admin, "GET", "/api/admin/paiements?fournisseur=MTN_MOMO")
verifier("filtre par operateur",
         s == 200 and all(p["fournisseur"] == "MTN_MOMO" for p in b["contenu"]),
         f"{b['totalElements']} paiement(s) MTN")
s, b = call(admin, "GET", "/api/admin/paiements?recherche=677123456")
verifier("recherche par numero de telephone", s == 200 and b["totalElements"] > 0,
         f"{b['totalElements']} resultat(s)")
s, b = call(admin, "GET", f"/api/admin/paiements?recherche={ref_payee}")
verifier("recherche par reference", s == 200 and b["totalElements"] == 1, f"HTTP {s}")

titre("10. Fiche complete d'un paiement")
s, d = call(admin, "GET", f"/api/admin/paiements/{ref_payee}")
verifier("200", s == 200, f"HTTP {s}")
verifier("paiement + commande + tentatives + evenements",
         all(k in d for k in ["paiement", "commande", "tentatives", "evenements"]), "")
verifier("callbacks de l'operateur tracés", len(d["evenements"]) >= 1,
         f"{len(d['evenements'])} evenement(s) : "
         + ", ".join(e["statutRecu"] for e in d["evenements"]))
verifier("historique des tentatives", len(d["tentatives"]) >= 1, f"{len(d['tentatives'])}")
s, b = call(admin, "GET", f"/api/admin/commandes/{cmd_payee}/paiements")
verifier("tentatives d'une commande", s == 200 and len(b) >= 1, f"HTTP {s} / {len(b)}")
s, b = call(admin, "GET", "/api/admin/paiements/PAY-INEXISTANT")
verifier("reference inconnue -> 404", s == 404, f"HTTP {s}")

# ------------------------------------------------------------------
titre("11. Remboursement")
s, arts = call(admin, "GET", "/api/admin/articles?recherche=pantalon")
stock_avant = arts["contenu"][0]["stock"]
s, r = call(admin, "POST", f"/api/admin/paiements/{ref_payee}/remboursement",
            {"motif": "Article non conforme, geste commercial"},
            {"Idempotency-Key": str(uuid.uuid4())})
verifier("200", s == 200, f"HTTP {s} : {msg(r)}")
verifier("statut REMBOURSE", r["paiement"]["statut"] == "REMBOURSE", r["paiement"]["statut"])
verifier("indique si le transfert est automatique", "automatique" in r,
         f"automatique={r['automatique']}")
print(f"       consigne : {r['consigne']}")
s, arts = call(admin, "GET", "/api/admin/articles?recherche=pantalon")
stock_apres = arts["contenu"][0]["stock"]
verifier("stock restitue", stock_apres == stock_avant + 2, f"{stock_avant} -> {stock_apres}")
s, b = call(admin, "GET", f"/api/admin/commandes/{cmd_payee}")
verifier("commande annulee", b["statut"] == "ANNULEE", b["statut"])

titre("12. Remboursement : les cas refuses")
s, b = call(admin, "POST", f"/api/admin/paiements/{ref_payee}/remboursement",
            {"motif": "deuxieme tentative"})
verifier("deja rembourse -> 409", s == 409, f"HTTP {s} : {msg(b)}")
s, echecs = call(admin, "GET", "/api/admin/paiements?statut=ECHOUE&size=1")
if echecs["contenu"]:
    ref_echec = echecs["contenu"][0]["reference"]
    s, b = call(admin, "POST", f"/api/admin/paiements/{ref_echec}/remboursement",
                {"motif": "test"})
    verifier("paiement non reussi -> 409", s == 409, f"HTTP {s} : {msg(b)}")
s, b = call(admin, "POST", f"/api/admin/paiements/{ref_payee}/remboursement", {"motif": ""})
verifier("motif vide -> 400", s == 400, f"HTTP {s} : {json.dumps(b.get('champs'))}")

# ------------------------------------------------------------------
titre("13. Fiche client enrichie")
s, cl = call(admin, "GET", "/api/admin/clients?recherche=adrien")
idc = cl["contenu"][0]["id"]
s, d = call(admin, "GET", f"/api/admin/clients/{idc}")
verifier("200", s == 200, f"HTTP {s}")
verifier("statistiques d'achat", d["nombreCommandes"] > 0,
         f"{d['nombreCommandes']} commande(s), {d['nombreCommandesPayees']} payee(s), "
         f"total {d['totalDepense']}, panier moyen {d['panierMoyen']}")
verifier("dernieres commandes", isinstance(d["dernieresCommandes"], list),
         f"{len(d['dernieresCommandes'])}")
verifier("panier en cours visible", "panierEnCours" in d,
         "null" if d["panierEnCours"] is None else f"{len(d['panierEnCours']['lignes'])} ligne(s)")

titre("14. Recherche de clients")
s, b = call(admin, "GET", "/api/admin/clients?role=ADMIN")
verifier("filtre par role", s == 200 and all(c["role"] == "ADMIN" for c in b["contenu"]),
         f"{b['totalElements']} admin(s)")
s, b = call(admin, "GET", "/api/admin/clients?actif=true")
verifier("filtre par activation", s == 200 and all(c["actif"] for c in b["contenu"]),
         f"{b['totalElements']} actif(s)")

# ------------------------------------------------------------------
titre("15. Ajustement de stock")
s, arts = call(admin, "GET", "/api/admin/articles?recherche=Jordan")
art = arts["contenu"][0]
avant = art["stock"]
s, b = call(admin, "PATCH", f"/api/admin/articles/{art['idArticle']}/stock",
            {"variation": 5, "motif": "Reception fournisseur"})
verifier("reapprovisionnement +5", s == 200 and b["stock"] == avant + 5,
         f"{avant} -> {b.get('stock')}")
s, b = call(admin, "PATCH", f"/api/admin/articles/{art['idArticle']}/stock", {"variation": -5})
verifier("retrait -5", s == 200 and b["stock"] == avant, f"retour a {b.get('stock')}")
s, b = call(admin, "PATCH", f"/api/admin/articles/{art['idArticle']}/stock", {"variation": -99999})
verifier("stock negatif refuse -> 409", s == 409, f"HTTP {s} : {msg(b)}")
s, b = call(admin, "PATCH", f"/api/admin/articles/{art['idArticle']}/stock", {})
verifier("variation absente -> 400", s == 400, f"HTTP {s}")

titre("16. Validation du catalogue")
s, b = call(admin, "POST", "/api/admin/articles",
            {"libelle": "", "prix": -10, "stock": -3, "idCategorie": 1})
verifier("article invalide -> 400 avec detail par champ",
         s == 400 and len(b.get("champs", {})) >= 3, json.dumps(b.get("champs")))
s, b = call(admin, "POST", "/api/admin/articles",
            {"libelle": "Test", "prix": 1000, "stock": 1, "idCategorie": 1,
             "imageUrl": "pas-une-url"})
verifier("URL d'image invalide -> 400", s == 400, json.dumps(b.get("champs")))

# ------------------------------------------------------------------
titre("17. Gestion des roles")
s, b = call(admin, "PATCH", f"/api/admin/clients/{idc}/role", {"role": "ADMIN"})
verifier("promotion USER -> ADMIN", s == 200 and b["role"] == "ADMIN", f"HTTP {s}")
s, b = call(admin, "PATCH", f"/api/admin/clients/{idc}/role", {"role": "ADMIN"})
verifier("role identique -> 409", s == 409, f"HTTP {s} : {msg(b)}")
s, me = call(admin, "GET", "/api/admin/clients?recherche=ralph@gmail.com")
id_moi = me["contenu"][0]["id"]
s, b = call(admin, "PATCH", f"/api/admin/clients/{id_moi}/role", {"role": "USER"})
verifier("changer son propre role -> 403", s == 403, f"HTTP {s} : {msg(b)}")
s, b = call(admin, "PATCH", f"/api/admin/clients/{idc}/role", {"role": "USER"})
verifier("retrogradation ADMIN -> USER", s == 200 and b["role"] == "USER", f"HTTP {s}")
s, b = call(admin, "PATCH", f"/api/admin/clients/{id_moi}/activation")
verifier("blocage d'un admin refuse -> 409", s == 409, f"HTTP {s} : {msg(b)}")

# ------------------------------------------------------------------
titre("18. Moderation des avis")
s, b = call(admin, "GET", "/api/admin/avis?page=0&size=10")
verifier("liste paginee", s == 200 and "contenu" in b, f"HTTP {s} total={b.get('totalElements')}")
verifier("auteur et article exposes",
         not b["contenu"] or all("emailAuteur" in a and "libelleArticle" in a for a in b["contenu"]),
         f"{len(b['contenu'])} avis")
s, b = call(admin, "GET", "/api/admin/avis?noteMin=1&noteMax=2")
verifier("filtre par note", s == 200 and all(1 <= a["note"] <= 2 for a in b["contenu"]),
         f"{b['totalElements']} avis negatif(s)")

# ------------------------------------------------------------------
titre("19. Journal d'administration")
s, j = call(admin, "GET", "/api/admin/journal?size=10")
verifier("200", s == 200, f"HTTP {s}")
verifier("les actions de ce test sont tracees", j["totalElements"] > 0,
         f"{j['totalElements']} entree(s)")
for e in j["contenu"][:6]:
    print(f"       {e['dateAction'][:19]}  {e['acteur']:20} {e['action']:24} "
          f"{e['cibleType']}/{e['cibleId']}")
s, b = call(admin, "GET", "/api/admin/journal?action=PAIEMENT_REMBOURSE")
verifier("remboursement trace avec son motif",
         b["totalElements"] >= 1 and "geste commercial" in b["contenu"][0]["details"],
         b["contenu"][0]["details"][:100] if b["totalElements"] else "aucune entree")
s, b = call(admin, "GET", "/api/admin/journal?action=ROLE_CHANGE")
verifier("changements de role traces", b["totalElements"] >= 2, f"{b['totalElements']} entree(s)")
s, b = call(admin, "GET", "/api/admin/journal?cibleType=ARTICLE")
verifier("filtre par type de cible", s == 200 and all(e["cibleType"] == "ARTICLE" for e in b["contenu"]),
         f"{b['totalElements']} action(s) sur articles")

# ------------------------------------------------------------------
titre("20. Cloisonnement : un USER ne touche a rien de tout cela")
for route in ["/api/admin/tableau-de-bord", "/api/admin/paiements", "/api/admin/journal",
              "/api/admin/clients", "/api/admin/commandes"]:
    s, _ = call(client, "GET", route)
    verifier(f"USER sur {route} -> 403", s == 403, f"HTTP {s}")
s, _ = call(client, "POST", f"/api/admin/paiements/{ref_payee}/remboursement", {"motif": "x"})
verifier("USER ne peut pas rembourser -> 403", s == 403, f"HTTP {s}")

print(f"\n================  {sum(resultats)}/{len(resultats)} verifications reussies  ================")
if sum(resultats) != len(resultats):
    print("Echecs :")
    raise SystemExit(1)
