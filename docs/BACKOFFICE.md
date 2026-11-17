# Back-office administrateur

Tout est sous `/api/admin/**`, réservé à `ROLE_ADMIN`.

Trois principes appliqués partout :

1. **Tout est paginé.** Aucune liste ne peut grossir sans limite.
2. **Tout changement est tracé.** Journal d'audit consultable.
3. **Les agrégats sont calculés par la base.** Jamais de `findAll()` suivi
   de streams.

Vérification : `python scripts/test_admin.py` → 77 contrôles.

---

## 1. Tableau de bord

```
GET /api/admin/tableau-de-bord
GET /api/admin/tableau-de-bord?debut=2026-11-01&fin=2026-11-17
```

Sans paramètre : les **30 derniers jours**. Période maximale : 366 jours
(au-delà, `400`). `debut > fin` → `400`.

Un seul appel renvoie onze sections :

| Section | Contenu |
|---|---|
| `periode` | bornes effectivement appliquées + nombre de jours |
| `ventes` | CA, commandes payées/créées, panier moyen, articles vendus, taux de conversion, **évolution vs période précédente de même durée** |
| `aujourdhui` | CA du jour, commandes, nouveaux clients |
| `serie` | un point **par jour**, pour les courbes |
| `commandesParStatut` | répartition sur la période |
| `paiements` | par statut, **par opérateur**, motifs d'échec, taux de réussite |
| `meilleursArticles` | top 10 de la période |
| `meilleursClients` | top 10 par montant dépensé |
| `caParCategorie` | répartition du chiffre d'affaires |
| `clients` | total, actifs, bloqués, nouveaux sur la période |
| `alertes` | ce qui demande une action maintenant |

### Deux points de conception

**Le chiffre d'affaires vient des paiements réussis, jamais des commandes.**
Une commande non réglée n'est pas du revenu. La série temporelle est donc
indexée sur `datePaiement`, pas sur `dateCommande`.

**Les jours sans vente sont remplis à zéro.** Sans cela, une courbe front
relie le 3 au 7 en ignorant le creux, ce qui donne une lecture fausse.

**Une évolution sans point de comparaison vaut `null`, pas `0`.** Si la
période précédente était à zéro, « +∞ % » n'est pas une information.

### Santé des paiements

La section la plus utile maintenant qu'il y a un module de paiement :

```json
"paiements": {
  "tauxReussiteGlobal": 33.3,
  "parFournisseur": [
    { "libelle": "MTN Mobile Money", "total": 6, "reussis": 2,
      "echoues": 3, "expires": 1, "tauxReussite": 33.3,
      "montantEncaisse": 106400.00 }
  ],
  "motifsEchec": {
    "FONDS_INSUFFISANTS": 1, "REFUSE_PAR_UTILISATEUR": 1,
    "TIMEOUT": 1, "MONTANT_INCOHERENT": 1
  }
}
```

Savoir si l'on perd des ventes par solde insuffisant ou par abandon change
complètement les actions à mener.

### Alertes

```json
"alertes": {
  "commandesAPreparer": 3,      // payées, en attente d'expédition
  "paiementsEnCours": 1,        // tentatives encore en vol
  "paiementsBloques": 0,        // en vol et délai dépassé : signe d'un souci
  "articlesEnRupture": 0,
  "articlesStockFaible": 2,
  "seuilStockFaible": 5,        // ADMIN_SEUIL_STOCK_FAIBLE dans .env
  "stockFaible": [ { "idArticle": 3, "libelle": "Air Jordan Noir", "stock": 3 } ]
}
```

L'ancien `GET /api/admin/statistiques` est conservé tel quel pour ne pas
casser un front existant.

---

## 2. Suivi des paiements

C'était le trou le plus gênant : le module de paiement existait, mais
l'administrateur n'y avait aucun accès.

```
GET  /api/admin/paiements?statut=ECHOUE&fournisseur=MTN_MOMO
                         &debut=2026-11-01&fin=2026-11-17
                         &recherche=677123456&page=0&size=20
GET  /api/admin/paiements/{reference}
GET  /api/admin/commandes/{idCommande}/paiements
POST /api/admin/paiements/{reference}/remboursement
```

Le paramètre `recherche` couvre d'un seul coup la référence interne, la
référence opérateur, le numéro payeur, l'email et le nom du client : au
téléphone avec un client, on tape ce qu'il donne.

La fiche `GET /api/admin/paiements/{reference}` renvoie en un appel le
paiement, la commande, **toutes les tentatives** de cette commande et **les
callbacks reçus de l'opérateur** (table `evenement_paiement`). C'est la
piste d'audit du support.

Le numéro payeur est **en clair** dans la vue admin — indispensable pour
effectuer un remboursement manuel — alors qu'il est masqué (`*****3456`)
dans la vue client.

### Remboursement

```
POST /api/admin/paiements/PAY-8-1-B3104B55/remboursement
Headers : Idempotency-Key: <uuid>
Body    : { "motif": "Article non conforme, geste commercial" }
```

Déroulement :

1. verrou sur la ligne de paiement — deux administrateurs qui cliquent en
   même temps ne remboursent pas deux fois ;
2. transition `PAYE → REMBOURSE` validée par la machine à états — un
   paiement non réussi ou déjà remboursé est refusé (`409`) ;
3. appel à l'opérateur ;
4. annulation de la commande et restitution du stock **seulement si la
   marchandise n'est pas partie** ;
5. trace au journal avec le motif.

**Le champ `automatique` de la réponse est décisif :**

```json
{ "automatique": false,
  "consigne": "ATTENTION : l'operateur ne gere pas le remboursement automatique.
               Effectuez le transfert de 10000 XAF vers le 677123456 manuellement.
               Commande annulee et stock restitue." }
```

Chez MTN comme chez Orange, un remboursement passe par un produit distinct
de l'encaissement (Disbursement chez MTN, transfert marchand chez Orange)
qui demande une habilitation séparée. L'implémentation par défaut de
`PasserellePaiement.rembourser` renvoie donc `false` : le paiement est bien
marqué `REMBOURSE` dans le système, mais **l'argent n'a pas bougé**. Afficher
un succès sans le dire serait trompeur. En simulation, `automatique` vaut
`true`.

---

## 3. Listes, filtres et recherche

Toutes les listes acceptent la pagination et le tri standard de Spring Data :

```
?page=0&size=20&sort=dateCreation,desc
```

`size` est plafonné à 200 (`spring.data.web.pageable.max-page-size`), donc
un `?size=100000` ne contourne pas la pagination.

### Enveloppe de réponse

```json
{
  "contenu": [ ... ],
  "page": 0, "taille": 20,
  "totalElements": 7, "totalPages": 4,
  "premiere": true, "derniere": false
}
```

On ne renvoie pas directement un `Page<T>` de Spring Data : sa sérialisation
JSON n'est pas stable entre versions. `PageReponse<T>` fixe le contrat.

### Critères par ressource

| Route | Filtres |
|---|---|
| `GET /api/admin/commandes` | `statut`, `idClient`, `debut`, `fin`, `montantMin`, `recherche` (nom, prénom, email, libellé, n° de commande) |
| `GET /api/admin/paiements` | `statut`, `fournisseur`, `debut`, `fin`, `recherche` |
| `GET /api/admin/clients` | `role`, `actif`, `recherche` (nom, prénom, email, téléphone) |
| `GET /api/admin/articles` | `idCategorie`, `stockMax`, `prixMin`, `prixMax`, `recherche` |
| `GET /api/admin/avis` | `idArticle`, `noteMin`, `noteMax`, `recherche` |
| `GET /api/admin/journal` | `acteur`, `action`, `cibleType`, `cibleId`, `debut`, `fin` |

Les anciennes routes non paginées restent disponibles sous `/tous`
(`/api/admin/commandes/tous`, `/articles/tous`, `/clients/tous`,
`/avis/tous`) pour ne pas casser un front existant. **À ne pas utiliser sur
une base qui grossit.**

### Pourquoi l'API Criteria et pas du JPQL

Le motif naturel pour un filtre optionnel serait :

```sql
where (:statut is null or c.statut = :statut)
```

**PostgreSQL le refuse.** Un paramètre qui n'apparaît que dans un test
`is null` n'a aucun contexte permettant d'en déduire le type, et le serveur
répond `could not determine data type of parameter $5`. Les contournements
existent (casts explicites, valeurs sentinelles) mais encombrent la requête.

Avec l'API Criteria ([`Recherches`](../src/main/java/com/example/E_commerce/Repository/Recherches.java)),
un critère absent n'est **pas ajouté** à la requête. Conséquences : aucun
paramètre non typé, un SQL qui ne contient que les filtres réellement
demandés, et donc des plans d'exécution qui peuvent utiliser les index.

### N+1 sur les vues de liste

`CommandeResumeDto` expose `nombreLignes`, ce qui suppose de connaître les
lignes. Charger la collection ligne par ligne ferait 20 requêtes pour une
page de 20. `@BatchSize(size = 50)` sur `Commande.lignes` (et sur `Personne`
et `Article`) fait charger toutes les collections de la page **en une seule
requête**.

La vue de liste ne renvoie volontairement **pas** les lignes elles-mêmes :
le détail reste sur `GET /api/admin/commandes/{id}`.

---

## 4. Fiche client

```
GET /api/admin/clients/{idClient}
```

Ne renvoie plus seulement l'identité :

```json
{
  "client": { "id": 1, "nom": "noubam", "email": "...", "actif": true, "role": "USER" },
  "dateInscription": "2026-11-17T12:40:12Z",
  "nombreCommandes": 8,
  "nombreCommandesPayees": 2,
  "totalDepense": 106400.00,
  "panierMoyen": 53200.00,
  "dateDerniereCommande": "2026-11-17T12:48:55Z",
  "panierEnCours": { "lignes": [ ... ], "total": 25000.00 },
  "dernieresCommandes": [ ... ]
}
```

`dateInscription` a dû être ajoutée à `Personne` : elle n'existait pas, ce
qui rendait impossible le comptage des nouveaux clients. Les comptes créés
avant cet ajout l'ont à `null` et sont exclus des décomptes.

---

## 5. Stock

```
PATCH /api/admin/articles/{id}/stock
Body : { "variation": 10, "motif": "Reception fournisseur" }
```

Endpoint dédié plutôt qu'un `PUT` sur l'article entier : réapprovisionner ne
doit pas obliger à renvoyer prix, description et image, au risque de les
écraser.

On exprime une **variation**, pas une valeur absolue. « +10 reçus » est
l'opération réelle, et sous verrou pessimiste deux réapprovisionnements
simultanés **s'additionnent** au lieu de s'écraser. Un stock qui passerait
sous zéro est refusé (`409`).

---

## 6. Rôles

```
PATCH /api/admin/clients/{id}/role
Body : { "role": "ADMIN" }
```

Avant, le seul administrateur était celui du jeu de démonstration : en
production il fallait passer par du SQL manuel.

Deux garde-fous :

- **on ne modifie pas son propre rôle** (`403`) — on se verrouillerait
  dehors ;
- **on ne rétrograde pas le dernier administrateur** de la plateforme
  (`409`).

`PATCH /api/admin/clients/{id}/activation` bascule actif/inactif, et refuse
de bloquer un compte `ADMIN` (`409`).

---

## 7. Journal d'audit

```
GET /api/admin/journal
GET /api/admin/journal?action=PAIEMENT_REMBOURSE
GET /api/admin/journal?cibleType=ARTICLE&debut=2026-11-01
GET /api/admin/journal?acteur=ralph
```

```
2026-11-17T12:49:07  ralph@gmail.com  PAIEMENT_REMBOURSE  PAIEMENT/PAY-8-1-B3104B55
2026-11-17T12:49:50  ralph@gmail.com  STOCK_AJUSTE        ARTICLE/3
2026-11-17T12:49:59  ralph@gmail.com  ROLE_CHANGE         CLIENT/1
```

Actions tracées : `ARTICLE_CREE`, `ARTICLE_MODIFIE`, `ARTICLE_SUPPRIME`,
`ARTICLE_RETIRE_VENTE`, `STOCK_AJUSTE`, `CATEGORIE_CREEE`,
`CATEGORIE_MODIFIEE`, `CATEGORIE_SUPPRIMEE`, `COMMANDE_STATUT_CHANGE`,
`CLIENT_BLOQUE`, `CLIENT_DEBLOQUE`, `ROLE_CHANGE`, `AVIS_SUPPRIME`,
`PAIEMENT_REMBOURSE`.

Le champ `details` dit **ce qui a changé** et non juste « modifié » :

```
"prix 5000.00 -> 6500.00 | stock 7 -> 12"
"Montant 10000.00 XAF | commande 8 | automatique=true | motif : Article non conforme"
"adrien@gmail.com : USER -> ADMIN"
```

Deux choix de conception :

- **l'écriture se fait dans la transaction de l'action tracée.** Si l'action
  est annulée, la ligne de journal disparaît avec elle : on ne journalise
  que ce qui a réellement eu lieu ;
- **l'acteur est lu dans le contexte de sécurité**, sans requête en base :
  tracer ne doit rien coûter.

---

## 8. Machine à états des commandes

`PATCH /api/admin/commandes/{id}/statut?statut=EN_COURS`

```
EN_ATTENTE ──> ANNULEE
PAYEE ──────> EN_COURS ──> LIVREE
  │               │
  └──> ANNULEE <──┘
```

`EN_ATTENTE → PAYEE` est **volontairement absent** : seul un paiement
confirmé fait passer une commande à `PAYEE`, jamais un clic d'administrateur.
Toute autre transition renvoie `409`, un statut inconnu renvoie `400`.

Annuler une commande réglée **rend le stock** : sans cela il resterait
réservé pour une commande qui ne partira jamais.

---

## 9. Validation des entrées

`@Valid` sur tous les corps de requête. Les erreurs listent les champs :

```json
{
  "statut": 400,
  "message": "Donnees invalides",
  "champs": {
    "libelle": "Le libelle est obligatoire",
    "prix": "Le prix doit etre au moins de 1 FCFA",
    "stock": "Le stock ne peut pas etre negatif"
  }
}
```

Contraintes sur un article : libellé non vide (200 car. max), prix ≥ 1,
stock entre 0 et 1 000 000, `imageUrl` commençant par `http://` ou
`https://`.

---

## 10. Ce qui reste à faire

Dit franchement, pour ne pas laisser croire que le back-office est complet :

- **Pas d'export CSV / Excel.** Les données sont filtrables mais pas
  téléchargeables.
- **Pas d'upload d'images.** `imageUrl` reste une URL externe ; il n'y a pas
  de stockage de fichiers.
- **Pas d'opérations en lot** (changer le statut de 50 commandes d'un coup).
- **Le journal n'est pas purgé.** Il grossit indéfiniment ; il faudra une
  rétention (12 mois par exemple).
- **Pas de verrou distribué sur les tâches de fond.** En multi-instance, les
  jobs de réconciliation et d'expiration tournent sur chaque nœud. Les
  verrous de ligne évitent la corruption, mais pour éviter le travail en
  double il faudrait ShedLock.
- **Le remboursement automatique n'est pas implémenté côté opérateur réel** —
  voir §2, c'est un choix assumé et signalé à l'utilisateur.
