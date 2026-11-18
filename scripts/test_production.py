"""
Parcours client complet contre la boutique deployee.

Deux conditions reproduisent ce que fait reellement le navigateur, et que
des appels curl ordinaires ne reproduisent pas :

  1. Tout passe par le relais de l'hebergeur du front, pas par l'API en
     direct.
  2. Chaque requete porte l'en-tete Origin du front. C'est le point qui
     avait echappe a la verification precedente : un GET same-origin
     n'envoie pas d'Origin, un POST si. Un filtre CORS qui ne connait pas
     l'origine repond 403 avant d'atteindre le moindre controleur, et
     seules les ecritures echouent.

Le compte cree est un compte de test, identifiable a son email, et peut
etre supprime depuis le back-office.
"""

import http.cookiejar
import json
import secrets
import sys
import time
import urllib.error
import urllib.request
import uuid

FRONT = "https://qalf-shop.vercel.app"
ORIGINE = FRONT

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
resultats = []


def call(methode, chemin, corps=None, entetes=None):
    data = json.dumps(corps).encode() if corps is not None else None
    req = urllib.request.Request(FRONT + chemin, data=data, method=methode)
    req.add_header("Content-Type", "application/json")
    # L'en-tete que le navigateur pose sur toute ecriture, meme same-origin.
    req.add_header("Origin", ORIGINE)
    req.add_header("Referer", FRONT + "/")
    for k, v in (entetes or {}).items():
        req.add_header(k, v)
    try:
        with opener.open(req, timeout=180) as r:
            txt = r.read().decode()
            return r.status, dict(r.headers), (json.loads(txt) if txt.strip() else None)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, dict(e.headers), json.loads(txt)
        except Exception:
            return e.code, dict(e.headers), txt
    except Exception as e:  # reseau, delai
        return 0, {}, str(e)


def verifier(libelle, condition, detail=""):
    resultats.append(bool(condition))
    etat = "OK   " if condition else "ECHEC"
    print(f"  [{etat}] {libelle}" + (f"  -> {detail}" if detail else ""))


def titre(t):
    print(f"\n--- {t} ---")


def attendre_terminal(ref, maxi=15):
    dernier = None
    for _ in range(maxi):
        s, _, b = call("GET", f"/api/user/paiements/{ref}")
        dernier = b
        if isinstance(b, dict) and b.get("terminal"):
            return b
        time.sleep(2)
    return dernier


# ==================================================================
suffixe = secrets.token_hex(4)
EMAIL = f"test.recette.{suffixe}@example.com"
MOTDEPASSE = "Recette-" + secrets.token_urlsafe(12)

print(f"Cible  : {FRONT}")
print(f"Origine: {ORIGINE}")
print(f"Compte : {EMAIL}")

titre("1. Lecture publique (aucune session)")
s, _, b = call("GET", "/api/parametres")
verifier("parametres de la boutique", s == 200, f"HTTP {s} nom={b.get('nom') if isinstance(b, dict) else b}")
verifier("mode simulation actif", isinstance(b, dict) and b.get("paiementSimule") is True,
         str(b.get("paiementSimule") if isinstance(b, dict) else b))

s, _, articles = call("GET", "/api/personne/Allarticle")
verifier("catalogue public", s == 200 and isinstance(articles, list) and len(articles) > 0,
         f"HTTP {s} {len(articles) if isinstance(articles, list) else 0} articles")

dispo = [a for a in (articles or []) if (a.get("stock") or 0) >= 3][:3]
verifier("au moins 2 articles en stock pour le test", len(dispo) >= 2, f"{len(dispo)} trouves")
if len(dispo) < 2:
    print("\nArret : pas assez de stock pour derouler le parcours.")
    sys.exit(1)

titre("2. Inscription  (le 403 signale par l'utilisateur)")
s, _, b = call("POST", "/api/personne/register", {
    "nom": "Recette",
    "prenom": "Compte",
    "email": EMAIL,
    "motDePasse": MOTDEPASSE,
    "numeroTelephone": "677000000",
})
verifier("compte cree", s in (200, 201), f"HTTP {s} : {b if not isinstance(b, dict) else b.get('message', 'ok')}")
if s == 403:
    print("\n  >>> 403 a l'inscription : le filtre CORS refuse l'origine du front.")
    print("      Rien n'atteint le controleur. C'est la panne signalee.")

titre("3. Connexion")
s, _, b = call("POST", "/api/auth/login", {"email": EMAIL, "password": MOTDEPASSE})
verifier("session ouverte", s == 200, f"HTTP {s} role={b.get('role') if isinstance(b, dict) else b}")
verifier("cookie de session recu", any(c.name == "QALFSESSION" for c in jar),
         ", ".join(c.name for c in jar) or "aucun")

titre("4. Panier")
call("DELETE", "/api/user/viderPanier")
s, _, b = call("POST", "/api/user/ajoutPanier", {"idArticle": dispo[0]["id"], "quantite": 2})
verifier("ajout au panier", s == 200, f"HTTP {s} total={b.get('total') if isinstance(b, dict) else b}")
s, _, pan = call("GET", "/api/user/monPanier")
verifier("panier relu", s == 200 and isinstance(pan, dict) and len(pan.get("lignes", [])) == 1,
         f"HTTP {s}")

titre("5. Paiement MTN qui reussit (677123456)")
cle = str(uuid.uuid4())
s, h, b = call("POST", "/api/user/commandes",
               {"fournisseur": "MTN_MOMO", "numeroPayeur": "677123456"},
               {"Idempotency-Key": cle})
verifier("commande creee (201)", s == 201, f"HTTP {s} : {b if not isinstance(b, dict) else ''}")
ref = idc = None
if s == 201:
    ref = b["paiement"]["reference"]
    idc = b["commande"]["idCommande"]
    verifier("statut initial EN_COURS", b["paiement"]["statut"] == "EN_COURS", b["paiement"]["statut"])
    verifier("numero masque", str(b["paiement"]["numeroPayeur"]).startswith("*"),
             b["paiement"]["numeroPayeur"])

    titre("6. Idempotence : meme cle rejouee")
    s2, h2, b2 = call("POST", "/api/user/commandes",
                      {"fournisseur": "MTN_MOMO", "numeroPayeur": "677123456"},
                      {"Idempotency-Key": cle})
    verifier("rejeu signale", h2.get("Idempotent-Replay") == "true", str(h2.get("Idempotent-Replay")))
    verifier("aucun second debit", isinstance(b2, dict) and b2["paiement"]["reference"] == ref,
             b2["paiement"]["reference"] if isinstance(b2, dict) else str(b2))

    titre("7. Notification simulee -> PAYE")
    fin = attendre_terminal(ref)
    verifier("statut final PAYE", isinstance(fin, dict) and fin.get("statut") == "PAYE",
             f"{fin.get('statut') if isinstance(fin, dict) else fin}")

    titre("8. Commande passee a PAYEE")
    s, _, cmds = call("GET", "/api/user/mesCommandes")
    c = next((x for x in (cmds or []) if x["idCommande"] == idc), None)
    verifier("commande PAYEE", c is not None and c["statut"] == "PAYEE",
             c["statut"] if c else "introuvable")

    titre("9. Avis sur l'article achete")
    s, _, b = call("POST", "/api/user/laisserAvis",
                   {"idArticle": dispo[0]["id"], "note": 5,
                    "commentaire": "Commande de recette automatique."})
    verifier("avis accepte", s in (200, 201), f"HTTP {s} : {b if not isinstance(b, dict) else 'ok'}")

titre("10. Paiement qui echoue (677123450, fonds insuffisants)")
call("POST", "/api/user/ajoutPanier", {"idArticle": dispo[1]["id"], "quantite": 1})
s, _, b = call("POST", "/api/user/commandes",
               {"fournisseur": "MTN_MOMO", "numeroPayeur": "677123450"},
               {"Idempotency-Key": str(uuid.uuid4())})
verifier("commande creee", s == 201, f"HTTP {s}")
if s == 201:
    ref_echec = b["paiement"]["reference"]
    idc_echec = b["commande"]["idCommande"]
    fin2 = attendre_terminal(ref_echec)
    verifier("statut final ECHOUE", isinstance(fin2, dict) and fin2.get("statut") == "ECHOUE",
             f"{fin2.get('statut') if isinstance(fin2, dict) else fin2} / "
             f"{fin2.get('codeErreur') if isinstance(fin2, dict) else ''}")
    s, _, cmds = call("GET", "/api/user/mesCommandes")
    c2 = next((x for x in (cmds or []) if x["idCommande"] == idc_echec), None)
    verifier("commande annulee, stock rendu", c2 is not None and c2["statut"] == "ANNULEE",
             c2["statut"] if c2 else "introuvable")

titre("11. Numero Orange envoye a MTN -> refus sans commande orpheline")
call("POST", "/api/user/ajoutPanier", {"idArticle": dispo[0]["id"], "quantite": 1})
s, _, avant = call("GET", "/api/user/mesCommandes")
n_avant = len(avant or [])
s, _, b = call("POST", "/api/user/commandes",
               {"fournisseur": "MTN_MOMO", "numeroPayeur": "699123456"})
verifier("400 Bad Request", s == 400, f"HTTP {s}")
s, _, apres = call("GET", "/api/user/mesCommandes")
verifier("aucune commande orpheline", len(apres or []) == n_avant, f"{n_avant} -> {len(apres or [])}")

titre("12. Cloisonnement : un client sur /api/admin")
s, _, b = call("GET", "/api/admin/tableau-de-bord")
verifier("403 pour un client", s == 403, f"HTTP {s}")

titre("13. Deconnexion puis acces protege")
call("POST", "/api/auth/logout")
jar.clear()
s, _, b = call("GET", "/api/user/monPanier")
verifier("401 sans session (pas 403)", s == 401, f"HTTP {s}")

# ==================================================================
ok = sum(1 for r in resultats if r)
print(f"\n==================  {ok}/{len(resultats)} controles passes  ==================")
print(f"Compte de test a supprimer depuis le back-office : {EMAIL}")
sys.exit(0 if ok == len(resultats) else 1)
