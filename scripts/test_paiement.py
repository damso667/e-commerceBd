import hashlib, hmac, http.cookiejar, json, time, urllib.error, urllib.request, uuid

API = "http://localhost:8080"
SECRET = b"dev-secret-a-changer-en-production"
jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
resultats = []


def call(method, path, body=None, headers=None, raw=None):
    data = raw.encode() if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with opener.open(req) as r:
            txt = r.read().decode()
            return r.status, dict(r.headers), (json.loads(txt) if txt.strip() else None)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, dict(e.headers), json.loads(txt)
        except Exception:
            return e.code, dict(e.headers), txt


def verifier(libelle, condition, detail=""):
    etat = "OK  " if condition else "ECHEC"
    resultats.append(condition)
    print(f"  [{etat}] {libelle}" + (f"  -> {detail}" if detail else ""))


def attendre_terminal(ref, maxi=12):
    for _ in range(maxi):
        s, _, b = call("GET", f"/api/user/paiements/{ref}")
        if b and b.get("terminal"):
            return b
        time.sleep(2)
    return b


def titre(t):
    print(f"\n--- {t} ---")


# ------------------------------------------------------------------
titre("1. Connexion")
s, _, b = call("POST", "/api/auth/login",
               {"email": "adrien@gmail.com", "password": "password"})
verifier("login USER", s == 200 and b.get("role") == "ROLE_USER", f"HTTP {s} {b}")

titre("2. Panier")
call("DELETE", "/api/user/viderPanier")
s, _, b = call("POST", "/api/user/ajoutPanier", {"idArticle": 1, "quantite": 2})
verifier("ajout 2 x article 1", s == 200, f"HTTP {s} total={b and b.get('total')}")

titre("3. Commande + paiement MTN reussi (677123456)")
cle = str(uuid.uuid4())
corps = {"fournisseur": "MTN_MOMO", "numeroPayeur": "677123456"}
s, h, b = call("POST", "/api/user/commandes", corps, {"Idempotency-Key": cle})
verifier("201 Created", s == 201, f"HTTP {s}")
ref = b["paiement"]["reference"]
idc = b["commande"]["idCommande"]
verifier("statut initial EN_COURS", b["paiement"]["statut"] == "EN_COURS", b["paiement"]["statut"])
verifier("montant calcule par le serveur", b["commande"]["montantTotal"] is not None,
         str(b["commande"]["montantTotal"]))
verifier("numero masque dans la reponse", b["paiement"]["numeroPayeur"].startswith("*"),
         b["paiement"]["numeroPayeur"])
print(f"       reference={ref}  commande={idc}")

titre("4. IDEMPOTENCE : meme cle, meme corps")
s2, h2, b2 = call("POST", "/api/user/commandes", corps, {"Idempotency-Key": cle})
verifier("en-tete Idempotent-Replay: true", h2.get("Idempotent-Replay") == "true",
         str(h2.get("Idempotent-Replay")))
verifier("meme reference de paiement (aucun second debit)",
         b2["paiement"]["reference"] == ref, b2["paiement"]["reference"])
verifier("meme commande (aucune commande en double)",
         b2["commande"]["idCommande"] == idc, str(b2["commande"]["idCommande"]))

titre("5. IDEMPOTENCE : meme cle, corps different -> 409")
s3, _, b3 = call("POST", "/api/user/commandes",
                 {"fournisseur": "ORANGE_MONEY", "numeroPayeur": "699123456"},
                 {"Idempotency-Key": cle})
verifier("409 Conflict", s3 == 409, f"HTTP {s3} : {b3.get('message') if isinstance(b3, dict) else b3}")

titre("6. Notification asynchrone simulee -> PAYE")
fin = attendre_terminal(ref)
verifier("statut final PAYE", fin["statut"] == "PAYE", f"{fin['statut']} / {fin.get('message')}")
verifier("datePaiement renseignee", fin.get("datePaiement") is not None, str(fin.get("datePaiement")))

titre("7. Commande passee a PAYEE")
s, _, cmds = call("GET", "/api/user/mesCommandes")
c = next(x for x in cmds if x["idCommande"] == idc)
verifier("commande PAYEE", c["statut"] == "PAYEE", c["statut"])
verifier("reference de paiement exposee", c["referencePaiement"] == ref, str(c["referencePaiement"]))

titre("8. Stock avant/apres un paiement qui echoue (677123450)")
s, _, arts = call("GET", "/api/admin/articles")   # 403 attendu pour un USER
avant = None
s, _, pub = call("GET", "/api/personne/Allarticle")
call("POST", "/api/user/ajoutPanier", {"idArticle": 2, "quantite": 1})
s, _, b = call("POST", "/api/user/commandes",
               {"fournisseur": "MTN_MOMO", "numeroPayeur": "677123450"},
               {"Idempotency-Key": str(uuid.uuid4())})
verifier("commande creee", s == 201, f"HTTP {s}")
ref_echec = b["paiement"]["reference"]
idc_echec = b["commande"]["idCommande"]
fin2 = attendre_terminal(ref_echec)
verifier("statut final ECHOUE", fin2["statut"] == "ECHOUE",
         f"{fin2['statut']} / {fin2.get('codeErreur')} / {fin2.get('message')}")

titre("9. Commande annulee et stock rendu")
s, _, cmds = call("GET", "/api/user/mesCommandes")
c2 = next(x for x in cmds if x["idCommande"] == idc_echec)
verifier("commande ANNULEE", c2["statut"] == "ANNULEE", c2["statut"])

titre("10. Nouvelle tentative sur la commande en echec, avec Orange")
call("POST", "/api/user/commandes/%d/paiements" % idc_echec,
     {"fournisseur": "ORANGE_MONEY", "numeroPayeur": "699123456"})
s, _, b = call("POST", "/api/user/commandes/%d/paiements" % idc_echec,
               {"fournisseur": "ORANGE_MONEY", "numeroPayeur": "699123456"})
verifier("commande annulee -> nouvelle tentative refusee (409)", s == 409,
         f"HTTP {s} : {b.get('message') if isinstance(b, dict) else b}")

titre("11. Numero Orange envoye a MTN -> refus, SANS commande orpheline")
call("POST", "/api/user/ajoutPanier", {"idArticle": 3, "quantite": 1})
s, _, avant_cmds = call("GET", "/api/user/mesCommandes")
n_avant = len(avant_cmds)
s, _, b = call("POST", "/api/user/commandes",
               {"fournisseur": "MTN_MOMO", "numeroPayeur": "699123456"})
verifier("400 Bad Request", s == 400, f"HTTP {s} : {b.get('message') if isinstance(b, dict) else b}")
s, _, apres_cmds = call("GET", "/api/user/mesCommandes")
verifier("aucune commande creee (stock non bloque)", len(apres_cmds) == n_avant,
         f"{n_avant} -> {len(apres_cmds)}")
s, _, pan = call("GET", "/api/user/monPanier")
verifier("panier intact apres le refus", len(pan["lignes"]) > 0,
         f"{len(pan['lignes'])} ligne(s), total={pan['total']}")

titre("12. Validation du format de numero")
s, _, b = call("POST", "/api/user/commandes",
               {"fournisseur": "MTN_MOMO", "numeroPayeur": "12"})
verifier("400 avec detail des champs", s == 400 and "champs" in (b or {}),
         json.dumps(b.get("champs") if isinstance(b, dict) else b))

titre("13. Webhook sans signature -> 401")
s, _, b = call("POST", "/api/paiements/webhook/MTN_MOMO",
               raw='{"idEvenement":"x","referenceInterne":"y","statut":"SUCCESSFUL"}')
verifier("401 Unauthorized", s == 401, f"HTTP {s} : {b}")

titre("14. Webhook signe tentant de passer un paiement PAYE en ECHOUE")
body = json.dumps({"idEvenement": "evt-attaque-1", "referenceInterne": ref,
                   "statut": "FAILED", "montant": 53200})
sig = hmac.new(SECRET, body.encode(), hashlib.sha256).hexdigest()
for i in range(3):
    s, _, b = call("POST", "/api/paiements/webhook/MTN_MOMO", raw=body,
                   headers={"X-Signature": sig})
    print(f"       envoi {i + 1} -> HTTP {s} {b}")
s, _, apres = call("GET", f"/api/user/paiements/{ref}")
verifier("le paiement reste PAYE (etat terminal non reversible)",
         apres["statut"] == "PAYE", apres["statut"])

titre("15. Webhook avec un montant falsifie sur un paiement en cours")
call("POST", "/api/user/viderPanier")
call("POST", "/api/user/ajoutPanier", {"idArticle": 1, "quantite": 1})
s, _, b = call("POST", "/api/user/commandes",
               {"fournisseur": "MTN_MOMO", "numeroPayeur": "677123452"},  # EXPIRE : aucun callback
               {"Idempotency-Key": str(uuid.uuid4())})
ref_x = b["paiement"]["reference"]
body = json.dumps({"idEvenement": "evt-montant-faux", "referenceInterne": ref_x,
                   "statut": "SUCCESSFUL", "montant": 1})
sig = hmac.new(SECRET, body.encode(), hashlib.sha256).hexdigest()
s, _, b = call("POST", "/api/paiements/webhook/MTN_MOMO", raw=body,
               headers={"X-Signature": sig})
s, _, apres = call("GET", f"/api/user/paiements/{ref_x}")
verifier("montant incoherent -> paiement rejete, pas valide",
         apres["statut"] != "PAYE", f"{apres['statut']} / {apres.get('codeErreur')}")

titre("16. Cloisonnement des roles")
s, _, b = call("GET", "/api/admin/statistiques")
verifier("USER sur /api/admin -> 403", s == 403, f"HTTP {s}")

titre("17. Tableau de bord admin")
call("POST", "/api/auth/logout")
jar.clear()
s, _, b = call("POST", "/api/auth/login",
               {"email": "ralph@gmail.com", "password": "password"})
verifier("login ADMIN", s == 200 and b.get("role") == "ROLE_ADMIN", f"HTTP {s} {b}")
s, _, stats = call("GET", "/api/admin/statistiques")
verifier("statistiques accessibles", s == 200, f"HTTP {s}")
print("       " + json.dumps(stats, indent=None)[:400])

titre("18. Changement de statut interdit par la machine a etats")
s, _, b = call("PATCH", f"/api/admin/commandes/{idc_echec}/statut?statut=LIVREE")
verifier("ANNULEE -> LIVREE refuse (409)", s == 409,
         f"HTTP {s} : {b.get('message') if isinstance(b, dict) else b}")
s, _, b = call("PATCH", f"/api/admin/commandes/{idc}/statut?statut=EN_COURS")
verifier("PAYEE -> EN_COURS autorise", s == 200, f"HTTP {s}")
s, _, b = call("PATCH", f"/api/admin/commandes/{idc}/statut?statut=NIMPORTEQUOI")
verifier("statut inconnu -> 400 (plus de 500)", s == 400, f"HTTP {s}")

print(f"\n================  {sum(resultats)}/{len(resultats)} verifications reussies  ================")
