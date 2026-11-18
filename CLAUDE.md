# CLAUDE.md — API E-commerce

Contexte permanent de l'API. À lire avant toute intervention.

Front associé : dépôt séparé `damso667/e-commerceFront` (Angular 20).
Voir son propre `CLAUDE.md` pour les conventions côté interface.

---

## 1. Ce que c'est

API REST Spring Boot 4 / Java 21 / PostgreSQL (Supabase), déployée sur
Render en Docker.

| | |
|---|---|
| **En ligne** | https://ecommerce-api-60sl.onrender.com |
| **Service Render** | `ecommerce-api` — `srv-dbu5j1cipgvc73aplql0`, région `frankfurt` |
| **Base** | Supabase, pooler transaction `eu-central-1`, port 6543 |
| **Dépôt** | `damso667/e-commerceBd` |

Documentation détaillée : `docs/PAIEMENT.md`, `docs/BACKOFFICE.md`,
`docs/DEPLOIEMENT.md`.

---

## 2. Pièges de configuration

**Ne jamais nommer une variable d'environnement `USERNAME`, `PATH`, `TEMP`
ou `HOME`.** Sous Windows, `USERNAME` est une variable système, et les
variables d'environnement du système ont **priorité** sur les fichiers de
configuration Spring. D'où le préfixe `DB_` sur toutes les variables de
base. C'est ce qui empêchait l'application de démarrer au départ.

**Spring ne lit pas `.env` tout seul.** Le chargement se fait par
`spring.config.import=optional:file:./.env[.properties]` dans
`application.properties`. Le suffixe `[.properties]` dit comment analyser
le fichier ; un `#` en milieu de valeur y reste littéral, ce qui est
nécessaire pour un mot de passe comme `1234#Adrien667`.

**`prepareThreshold=0` est obligatoire** sur le pooler Supabase en mode
transaction (port 6543) : il ne gère pas les prepared statements côté
serveur de PgJDBC. Sans ce paramètre, `prepared statement "S_1" already
exists` apparaît dès la deuxième requête identique.

**Render n'a pas de runtime Java.** D'où le `Dockerfile` multi-étapes.
`MaxRAMPercentage=70` est nécessaire : sinon la JVM dimensionne son tas sur
la RAM de l'hôte et l'instance se fait tuer sur les 512 Mo du plan gratuit.

**`SPRING_PROFILES_ACTIVE=prod` ne doit jamais être retiré.** Il déclenche
`SecurisationProduction`, qui désactive les comptes de démonstration restés
en base (`adrien@gmail.com`, `ralph@gmail.com`, mot de passe `password`) et
crée l'administrateur depuis `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

---

## 3. Pièges d'implémentation

**PostgreSQL refuse `:param is null or ...` en JPQL.** Un paramètre qui
n'apparaît que dans un test `is null` n'a aucun contexte de typage, et le
serveur répond `could not determine data type of parameter $5`. Toutes les
recherches multi-critères passent donc par l'API Criteria
(`Repository/Recherches.java`) : un critère absent n'est pas ajouté à la
requête.

**`@Enumerated(EnumType.STRING)` sur tout enum persisté.** Sans lui, JPA
stocke la position dans l'enum et ajouter une valeur change rétroactivement
le sens des lignes déjà en base.

**Verrou pessimiste sur le stock et sur les paiements.** Deux commandes
simultanées du dernier exemplaire passeraient toutes les deux ; un callback
d'opérateur et le job de réconciliation rendraient le stock deux fois.

**`spring.jpa.open-in-view=false`.** Les méthodes de service qui mappent
vers des DTO sont donc `@Transactional`, et les listes utilisent
`join fetch` ou `@BatchSize` pour éviter les N+1.

**`ddl-auto=update` n'est pas une stratégie de migration.** Il ajoute les
colonnes manquantes mais ne modifie ni ne supprime jamais rien. À remplacer
par Flyway avant toute évolution de schéma sérieuse.

---

## 4. Paiement — ce qu'il ne faut pas casser

Le module simule MTN MoMo et Orange Money sans aucun appel réseau, en
respectant les contraintes d'un vrai système. `app.paiement.mode` bascule
`simulation` / `reel` ; le service métier ne connaît que l'interface
`PasserellePaiement`.

**Idempotence à quatre niveaux** — chacun couvre un cas que les autres ne
couvrent pas :

1. **En-tête `Idempotency-Key`** : rejeu réseau, double-clic, rechargement.
2. **Métier** : une commande ne peut pas avoir deux tentatives actives ;
   une commande réglée refuse tout nouveau débit.
3. **Opérateur** : la référence interne sert d'identifiant de transaction
   (chez MTN, dérivée en UUID déterministe).
4. **Callback** : contrainte unique `(fournisseur, idEvenement)`, et un
   **statut terminal n'est jamais réécrit**.

**La machine à états de `StatutPaiement` est la garantie centrale.** Elle
interdit qu'un callback en retard défasse un paiement réussi. Ne jamais
changer un statut sans passer par `peutAllerVers`.

**Les jobs de fond sont indispensables**, pas du confort : aucun opérateur
ne garantit la livraison de ses webhooks. Réconciliation toutes les 30 s,
expiration toutes les 60 s, ramassage des commandes abandonnées toutes les
5 min.

Numéros de test déterministes : `...0` fonds insuffisants, `...1` refus,
`...2` expiration, `...3` compte introuvable, sinon succès.

---

## 5. White-label

`ParametresBoutique` (ligne unique) porte le nom, le slogan, la couleur, le
logo et l'icône. Raison : la plateforme est **revendue à plusieurs
marchands**, qui doivent l'exploiter sous leur marque sans fork.

- Lecture publique : `GET /api/parametres`, `/logo`, `/icone`.
- `GET /api/parametres/manifest.webmanifest` : le manifeste PWA est
  **engendré depuis la base**, donc le nom et l'icône de l'application
  installée suivent les paramètres. Un manifeste statique figerait la
  marque au build.
- Les binaires sont stockés **en base**, pas sur le disque : celui de
  Render est éphémère.
- **SVG refusé à l'upload** : il peut contenir du script et serait servi
  depuis l'origine de l'API. PNG, JPEG, WebP, 512 Ko maximum.

---

## 6. Commandes

```bash
./mvnw spring-boot:run                 # démarrage local sur 8080
./mvnw test                            # tests unitaires
python scripts/test_paiement.py        # 31 contrôles, parcours client
python scripts/test_admin.py           # 77 contrôles, back-office
python scripts/verifier_deploiement.py # instance Render, sans identifiants
```

```bash
render logs -r srv-dbu5j1cipgvc73aplql0 --tail --confirm
```

Les scripts Python exigent que le serveur tourne. `verifier_deploiement.py`
ne manipule aucun mot de passe, par choix.

---

## 7. Conventions

- Code, commentaires et messages d'erreur **en français**, sans accents
  dans les identifiants Java.
- Les commentaires expliquent **pourquoi**, pas quoi. Un commentaire qui
  paraphrase la ligne suivante est du bruit.
- Toute erreur métier passe par `Exception/` et ressort au format unique de
  `GestionnaireErreurs` (`horodatage`, `statut`, `erreur`, `message`,
  `chemin`, plus `champs` en validation).
- Toute mutation du back-office est tracée au journal d'audit, avec ce qui
  a changé et non juste « modifié ».
