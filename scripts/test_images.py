"""
Photos d'articles, de bout en bout, contre l'instance deployee.

Ce script a besoin d'une session administrateur. Il la prend dans
l'environnement et ne la conserve nulle part :

    ADMIN_EMAIL=... ADMIN_PASSWORD=... python scripts/test_images.py

Les valeurs sont celles des variables du meme nom sur Render. Rien n'est
ecrit sur disque, rien n'est affiche.

Il cree un article de test, y depose une photo, verifie qu'elle est servie,
que le SVG est refuse, que la photo se retire, puis retire l'article de la
vente. Au passage il controle que le journal d'audit a bien trace le depot.
"""

import base64
import http.cookiejar
import json
import os
import secrets
import sys
import urllib.error
import urllib.request

FRONT = os.environ.get("CIBLE", "https://qalf-shop.vercel.app")
ORIGINE = FRONT

# PNG 1x1 valide : il ne s'agit pas de juger une image mais de verifier
# qu'un binaire accepte fait l'aller-retour intact.
PNG_1X1 = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)
SVG = b'<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
resultats = []


def call(methode, chemin, corps=None):
    data = json.dumps(corps).encode() if corps is not None else None
    req = urllib.request.Request(FRONT + chemin, data=data, method=methode)
    req.add_header("Content-Type", "application/json")
    req.add_header("Origin", ORIGINE)
    return _envoyer(req)


def envoyer_fichier(chemin, nom_fichier, contenu, type_mime, champ="fichier"):
    """Multipart minimal : la bibliotheque standard n'en fabrique pas."""
    limite = "----qalf" + secrets.token_hex(12)
    corps = (
        f"--{limite}\r\n"
        f'Content-Disposition: form-data; name="{champ}"; filename="{nom_fichier}"\r\n'
        f"Content-Type: {type_mime}\r\n\r\n"
    ).encode() + contenu + f"\r\n--{limite}--\r\n".encode()

    req = urllib.request.Request(FRONT + chemin, data=corps, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={limite}")
    req.add_header("Origin", ORIGINE)
    return _envoyer(req)


def _envoyer(req):
    try:
        with opener.open(req, timeout=180) as r:
            brut = r.read()
            try:
                return r.status, dict(r.headers), json.loads(brut.decode())
            except Exception:
                return r.status, dict(r.headers), brut
    except urllib.error.HTTPError as e:
        brut = e.read()
        try:
            return e.code, dict(e.headers), json.loads(brut.decode())
        except Exception:
            return e.code, dict(e.headers), brut.decode(errors="replace")
    except Exception as e:
        return 0, {}, str(e)


def verifier(libelle, condition, detail=""):
    resultats.append(bool(condition))
    print(f"  [{'OK   ' if condition else 'ECHEC'}] {libelle}" + (f"  -> {detail}" if detail else ""))


def titre(t):
    print(f"\n--- {t} ---")


# ==================================================================
email = os.environ.get("ADMIN_EMAIL")
motdepasse = os.environ.get("ADMIN_PASSWORD")
if not email or not motdepasse:
    print("ADMIN_EMAIL et ADMIN_PASSWORD doivent etre definis dans l'environnement.")
    print("Ce sont les memes valeurs que les variables du meme nom sur Render.")
    sys.exit(2)

print(f"Cible : {FRONT}")

titre("1. Session administrateur")
s, _, b = call("POST", "/api/auth/login", {"email": email, "password": motdepasse})
verifier("connexion ADMIN", s == 200 and isinstance(b, dict) and b.get("role") == "ROLE_ADMIN",
         f"HTTP {s}")
if s != 200:
    print("\nArret : sans session administrateur, rien de la suite n'a de sens.")
    sys.exit(1)

titre("2. Article de test")
s, _, rayons = call("GET", "/api/admin/categories")
verifier("rayons lus", s == 200 and rayons, f"HTTP {s}")
id_rayon = rayons[0]["idCategorie"]

suffixe = secrets.token_hex(3)
s, _, article = call("POST", "/api/admin/articles", {
    "libelle": f"Article de recette {suffixe}",
    "description": "Cree par test_images.py, a retirer.",
    "prix": 1000,
    "stock": 1,
    "idCategorie": id_rayon,
})
verifier("article cree (201)", s == 201, f"HTTP {s}")
if s != 201:
    sys.exit(1)
id_article = article["idArticle"]
verifier("aucune photo au depart", not article.get("imageTeleversee"), str(article.get("imageUrl")))

titre("3. Image servie avant tout depot")
s, _, _ = call("GET", f"/api/personne/articleImage/{id_article}")
verifier("404 tant qu'aucune photo n'est deposee", s == 404, f"HTTP {s}")

titre("4. Depot d'une photo PNG")
s, _, apres = envoyer_fichier(f"/api/admin/articles/{id_article}/image",
                              "photo.png", PNG_1X1, "image/png")
verifier("depot accepte", s == 200, f"HTTP {s} : {apres if not isinstance(apres, dict) else ''}")
adresse = apres.get("imageUrl") if isinstance(apres, dict) else None
verifier("imageTeleversee passe a vrai", isinstance(apres, dict) and apres.get("imageTeleversee"),
         str(apres.get("imageTeleversee") if isinstance(apres, dict) else apres))
verifier("imageUrl pointe vers l'API, pas vers un tiers",
         bool(adresse) and adresse.startswith(f"/api/personne/articleImage/{id_article}?v="),
         str(adresse))

titre("5. Lecture publique de la photo")
s, entetes, contenu = call("GET", f"/api/personne/articleImage/{id_article}")
verifier("photo servie", s == 200, f"HTTP {s}")
verifier("type MIME conserve", entetes.get("Content-Type", "").startswith("image/png"),
         entetes.get("Content-Type", ""))
verifier("octets identiques a l'envoi", contenu == PNG_1X1,
         f"{len(contenu) if isinstance(contenu, bytes) else '?'} octets")
verifier("cache long avec ETag", "ETag" in entetes and "max-age" in entetes.get("Cache-Control", ""),
         f"{entetes.get('ETag')} / {entetes.get('Cache-Control')}")

titre("6. Le SVG est refuse")
s, _, b = envoyer_fichier(f"/api/admin/articles/{id_article}/image",
                          "piege.svg", SVG, "image/svg+xml")
verifier("400 Bad Request", s == 400, f"HTTP {s}")
verifier("le message explique pourquoi",
         isinstance(b, dict) and "SVG" in (b.get("message") or ""),
         (b.get("message") if isinstance(b, dict) else str(b))[:90])

titre("7. Fichier trop lourd")
s, _, b = envoyer_fichier(f"/api/admin/articles/{id_article}/image",
                          "enorme.png", PNG_1X1 + b"\0" * (1024 * 1024 + 10), "image/png")
verifier("refuse (400 ou 413)", s in (400, 413), f"HTTP {s}")

titre("8. La photo resiste a une modification de l'article")
s, _, modifie = call("PUT", f"/api/admin/articles/{id_article}", {
    "libelle": f"Article de recette {suffixe} (modifie)",
    "prix": 1200,
    "stock": 2,
    "idCategorie": id_rayon,
})
verifier("modification acceptee", s == 200, f"HTTP {s}")
verifier("la photo est toujours la",
         isinstance(modifie, dict) and modifie.get("imageTeleversee"),
         str(modifie.get("imageTeleversee") if isinstance(modifie, dict) else modifie))

titre("9. Journal d'audit")
s, _, journal = call("GET", "/api/admin/journal?size=20")
entrees = (journal or {}).get("contenu", []) if isinstance(journal, dict) else []
verifier("depot trace", any(e.get("action") == "ARTICLE_IMAGE_DEPOSEE" for e in entrees),
         ", ".join(sorted({e.get("action", "") for e in entrees}))[:90])

titre("10. Retrait de la photo")
s, _, vide = call("DELETE", f"/api/admin/articles/{id_article}/image")
verifier("retrait accepte", s == 200, f"HTTP {s}")
verifier("imageTeleversee repasse a faux",
         isinstance(vide, dict) and not vide.get("imageTeleversee"), str(vide))
s, _, _ = call("GET", f"/api/personne/articleImage/{id_article}")
verifier("404 de nouveau", s == 404, f"HTTP {s}")
s, _, b = call("DELETE", f"/api/admin/articles/{id_article}/image")
verifier("second retrait refuse (409)", s == 409, f"HTTP {s}")

titre("11. Menage")
s, _, b = call("DELETE", f"/api/admin/articles/{id_article}")
verifier("article retire", s == 200, f"HTTP {s} : {b.get('message') if isinstance(b, dict) else b}")
call("POST", "/api/auth/logout")

# ==================================================================
ok = sum(1 for r in resultats if r)
print(f"\n==================  {ok}/{len(resultats)} controles passes  ==================")
sys.exit(0 if ok == len(resultats) else 1)
