# API E-commerce

API REST Spring Boot 4 / Java 21 / PostgreSQL (Supabase), avec paiement
mobile money simulé (MTN MoMo et Orange Money).

- Documentation du module de paiement : [docs/PAIEMENT.md](docs/PAIEMENT.md)

---

## Démarrage

### 1. Configurer `.env`

```bash
cp .env.example .env
```

Puis remplir les valeurs. Récupérer les identifiants Supabase dans
**Project Settings → Database → Connection pooling** (mode *Transaction*,
port **6543**) :

```properties
DB_HOST=aws-1-eu-central-1.pooler.supabase.com
DB_PORT=6543
DB_NAME=postgres
DB_USERNAME=postgres.<ref-projet>
DB_PASSWORD=<mot de passe>
```

### 2. Lancer

```bash
./mvnw spring-boot:run
```

L'API écoute sur `http://localhost:8080`.

Comptes de démonstration créés au premier démarrage (sauf avec le profil
`prod`) : `adrien@gmail.com` / `password` (USER) et `ralph@gmail.com` /
`password` (ADMIN).

### 3. Vérifier le flux de paiement

Serveur démarré, dans un autre terminal :

```bash
python scripts/test_paiement.py
```

Le script enchaîne 31 vérifications : idempotence, notification asynchrone,
rejeu de callback, falsification de montant, restitution du stock,
cloisonnement des rôles.

---

## Comment `.env` est lu

Spring Boot **ne lit pas** `.env` tout seul. Le chargement se fait par une
seule ligne dans `application.properties` :

```properties
spring.config.import=optional:file:./.env[.properties]
```

- `[.properties]` dit à Spring comment analyser un fichier sans extension
  connue. Un `#` en milieu de valeur reste littéral, ce qui est nécessaire
  pour un mot de passe comme `1234#Adrien667`.
- `optional:` permet à l'application de démarrer sans `.env`, en
  production, où les valeurs viennent des variables d'environnement du
  serveur.
- Aucune dépendance supplémentaire n'est requise.

> **Ne jamais nommer une variable `USERNAME`, `PATH`, `TEMP` ou `HOME`.**
> Sous Windows, `USERNAME` est déjà une variable système, et les variables
> d'environnement du système ont la **priorité** sur les fichiers de
> configuration. `${USERNAME}` renvoie donc le nom de session Windows, pas
> la valeur du `.env`. D'où le préfixe `DB_` sur toutes les variables de
> base de données.

### Spécificités du pooler Supabase

L'URL JDBC est construite dans `application.properties` :

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require&prepareThreshold=0
```

- `prepareThreshold=0` est **obligatoire** sur le port 6543. Le pooler en
  mode *transaction* ne gère pas les *prepared statements* côté serveur de
  PgJDBC ; sans ce paramètre, l'erreur `prepared statement "S_1" already
  exists` apparaît dès la deuxième requête identique.
- `sslmode=require` : Supabase refuse les connexions en clair.
- Le pool Hikari est limité à 5 connexions, le pooler partagé étant lui-même
  contingenté.

---

## Structure

```
src/main/java/com/example/E_commerce/
├── Config/           sécurité, utilisateur courant
├── Controller/       admin, client, auth, catalogue public
├── Dto/              objets d'entrée/sortie (validés)
├── Enum/             rôles, statuts de commande et de paiement
├── Exception/        exceptions métier + gestionnaire HTTP global
├── Idempotence/      mécanisme Idempotency-Key
├── Models/           entités JPA
├── Paiement/
│   ├── Config/       propriétés typées
│   ├── Controller/   endpoints client + webhook opérateur
│   ├── Dto/
│   ├── Gateway/      contrat PasserellePaiement
│   │   ├── Simulation/   MTN et Orange simulés
│   │   └── Reel/         squelettes des vraies API
│   └── Service/      service métier, signature, tâches de fond
├── Repository/
└── Services/         admin, client, catalogue
```

---

## Endpoints principaux

### Public

| Méthode | Route                                          | Rôle                   |
|---------|------------------------------------------------|------------------------|
| POST    | `/api/auth/login`                              | connexion (session)    |
| POST    | `/api/auth/logout`                             | déconnexion            |
| POST    | `/api/personne/register`                        | inscription            |
| GET     | `/api/personne/Allarticle`                      | catalogue              |
| GET     | `/api/personne/articleParCategorie/{id}`        | catalogue par catégorie|
| GET     | `/api/personne/avisArticle/{id}`                | avis d'un article      |
| POST    | `/api/paiements/webhook/{fournisseur}`          | callback opérateur     |

### Client (`ROLE_USER`)

| Méthode | Route                                     | Rôle                          |
|---------|-------------------------------------------|-------------------------------|
| POST    | `/api/user/ajoutPanier`                   | ajouter au panier             |
| GET     | `/api/user/monPanier`                     | voir le panier                |
| PATCH   | `/api/user/diminuerQuantite/{idArticle}`  | décrémenter                   |
| DELETE  | `/api/user/supprimerArticlePanier/{id}`   | retirer une ligne             |
| DELETE  | `/api/user/viderPanier`                   | vider                         |
| POST    | `/api/user/commandes`                     | commander **et payer**        |
| POST    | `/api/user/commandes/{id}/paiements`      | nouvelle tentative            |
| GET     | `/api/user/paiements/{reference}`         | statut du paiement            |
| GET     | `/api/user/mesCommandes`                  | historique                    |
| POST    | `/api/user/laisserAvis`                   | laisser un avis               |

`/api/user/passerCommande` reste accepté comme alias de
`POST /api/user/commandes`, pour ne pas casser le front existant.

### Admin (`ROLE_ADMIN`)

`/api/admin/articles`, `/api/admin/categories`, `/api/admin/commandes`,
`/api/admin/clients`, `/api/admin/avis`, `/api/admin/statistiques`.

---

## Format d'erreur

Toutes les erreurs partagent une seule forme, produite par
`GestionnaireErreurs` :

```json
{
  "horodatage": "2026-11-17T12:09:24Z",
  "statut": 409,
  "erreur": "Conflict",
  "message": "Stock insuffisant pour : Air Jordan Noir (reste 2)",
  "chemin": "/api/user/commandes"
}
```

Les erreurs de validation ajoutent le détail par champ :

```json
{ "statut": 400, "champs": { "numeroPayeur": "Numero camerounais invalide" } }
```

| Code | Signification                                             |
|------|-----------------------------------------------------------|
| 400  | données invalides                                         |
| 401  | identifiants incorrects, signature de webhook invalide    |
| 403  | rôle insuffisant, ressource d'un autre compte, compte bloqué |
| 404  | ressource inexistante                                     |
| 409  | règle métier ou conflit d'idempotence                     |

---

## Tests

```bash
./mvnw test                          # tests unitaires
python scripts/test_paiement.py      # bout en bout, serveur démarré
```
