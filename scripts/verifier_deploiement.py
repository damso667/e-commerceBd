"""Verification de l'API deployee, sans aucun identifiant.

Ce script ne se connecte volontairement a aucun compte : il ne teste que
ce qui est observable de l'exterieur sans s'authentifier. Les controles qui
demandent une session sont listes dans docs/DEPLOIEMENT.md, a jouer soi-meme.

Usage :  python scripts/verifier_deploiement.py [url]
"""
import json, sys, urllib.error, urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "https://ecommerce-api-60sl.onrender.com").rstrip("/")
resultats = []


def appel(method, chemin, corps=None, entetes=None, timeout=60):
    data = corps.encode() if isinstance(corps, str) else None
    req = urllib.request.Request(BASE + chemin, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (entetes or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            txt = r.read().decode()
            return r.status, dict(r.headers), (json.loads(txt) if txt.strip() else None)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, dict(e.headers), json.loads(txt)
        except Exception:
            return e.code, dict(e.headers), txt
    except Exception as e:
        return 0, {}, str(e)


def verifier(libelle, condition, detail=""):
    resultats.append(bool(condition))
    print(f"  [{'OK  ' if condition else 'ECHEC'}] {libelle}" + (f"  -> {detail}" if detail else ""))


print(f"Cible : {BASE}\n")

print("--- 1. Disponibilite ---")
s, _, b = appel("GET", "/actuator/health")
verifier("sonde de sante", s == 200 and isinstance(b, dict) and b.get("status") == "UP",
         f"HTTP {s} {b}")
verifier("aucun detail divulgue par la sonde",
         not isinstance(b, dict) or set(b.keys()) <= {"status"}, str(b))

print("\n--- 2. Catalogue public ---")
s, _, articles = appel("GET", "/api/personne/Allarticle")
verifier("liste des articles accessible sans compte", s == 200 and isinstance(articles, list),
         f"HTTP {s}, {len(articles) if isinstance(articles, list) else '?'} article(s)")
if isinstance(articles, list) and articles:
    a = articles[0]
    verifier("structure d'un article", all(k in a for k in ["id", "nom", "prix", "categorieId"]),
             f"{a.get('nom')} / {a.get('prix')}")
    s, _, parcat = appel("GET", f"/api/personne/articleParCategorie/{a['categorieId']}")
    verifier("filtre par categorie", s == 200 and isinstance(parcat, list),
             f"HTTP {s}, {len(parcat) if isinstance(parcat, list) else '?'} article(s)")
    s, _, avis = appel("GET", f"/api/personne/avisArticle/{a['id']}")
    verifier("avis d'un article", s == 200 and isinstance(avis, list), f"HTTP {s}")

print("\n--- 3. Cloisonnement sans session ---")
for chemin in ["/api/admin/tableau-de-bord", "/api/admin/paiements", "/api/admin/journal",
               "/api/user/monPanier", "/api/user/mesCommandes"]:
    s, _, _ = appel("GET", chemin)
    verifier(f"{chemin} refuse", s in (401, 403), f"HTTP {s}")

print("\n--- 4. Webhook de paiement ---")
s, _, b = appel("POST", "/api/paiements/webhook/MTN_MOMO",
                corps='{"idEvenement":"sonde","referenceInterne":"x","statut":"SUCCESSFUL"}')
verifier("callback sans signature rejete", s == 401, f"HTTP {s} : {b}")

print("\n--- 5. Route d'authentification ---")
# Aucune tentative de connexion ici : ce script ne manipule pas de mot de
# passe. On verifie seulement que la route existe et valide son entree.
# Pour la neutralisation des comptes de demonstration, la preuve est dans
# les journaux de demarrage :
#   render logs -r srv-dbu5j1cipgvc73aplql0 --text "compte de demonstration"
s, _, b = appel("POST", "/api/auth/login", corps='{}')
verifier("login present et validant son entree", s in (400, 401),
         f"HTTP {s} : {b.get('message') if isinstance(b, dict) else str(b)[:80]}")

print("\n--- 6. Gestion d'erreur ---")
s, _, b = appel("GET", "/api/personne/articleParCategorie/999999")
verifier("categorie inexistante -> liste vide ou 404", s in (200, 404), f"HTTP {s}")
s, _, b = appel("POST", "/api/personne/register", corps='{"email":"pas-un-email"}')
verifier("inscription invalide -> 400 detaille", s == 400 and "champs" in (b or {}),
         json.dumps(b.get("champs")) if isinstance(b, dict) and "champs" in b else str(b)[:120])

print(f"\n================  {sum(resultats)}/{len(resultats)} verifications reussies  ================")
sys.exit(0 if sum(resultats) == len(resultats) else 1)
