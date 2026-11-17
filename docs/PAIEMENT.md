# Module de paiement mobile money

Ce module simule MTN Mobile Money et Orange Money **sans aucun appel réseau**,
en respectant les contraintes d'un vrai système de paiement : idempotence,
notifications asynchrones, machine à états, réconciliation.

L'objectif est que le passage aux vraies API ne demande **que de la
configuration**, et pas de réécriture.

---

## 1. Parcours client

```
1. POST /api/user/commandes
   Headers : Idempotency-Key: <uuid généré par le front>
   Body    : { "fournisseur": "MTN_MOMO", "numeroPayeur": "677123456" }

   -> 201 {
        "commande": { "idCommande": 12, "statut": "EN_ATTENTE", "montantTotal": 51600 },
        "paiement": { "reference": "PAY-12-1-A1B2C3D4", "statut": "EN_COURS" },
        "intervallePollingSecondes": 3
      }

2. GET /api/user/paiements/PAY-12-1-A1B2C3D4      (toutes les 3 s)

   -> { "statut": "EN_COURS", "enAttenteDeValidation": true }
   puis
   -> { "statut": "PAYE", "terminal": true, "datePaiement": "..." }

3. En cas d'échec, nouvelle tentative :
   POST /api/user/commandes/12/paiements
   Body : { "fournisseur": "ORANGE_MONEY", "numeroPayeur": "699123456" }
```

Le montant **n'est jamais envoyé par le client** : il est recalculé à partir
des lignes de commande. C'était une faille de la version précédente (il
suffisait de poster `montant: 1`).

---

## 2. Numéros de test (résultat déterministe)

La passerelle simulée décide de l'issue d'après le **dernier chiffre** du
numéro. Les démonstrations sont donc reproductibles.

| Numéro         | Opérateur | Résultat                                      |
|----------------|-----------|-----------------------------------------------|
| `677123456`    | MTN       | **PAYE** après ~4 s                           |
| `699123456`    | Orange    | **PAYE** après ~4 s                           |
| `677123450`    | MTN       | **ECHOUE** — fonds insuffisants               |
| `677123451`    | MTN       | **ECHOUE** — refusé par l'utilisateur         |
| `677123452`    | MTN       | **EXPIRE** — le client ne valide jamais       |
| `677123453`    | MTN       | **ECHOUE immédiat** — compte introuvable      |
| `690123456`    | MTN       | **400** — préfixe Orange envoyé à MTN         |

Préfixes reconnus (Cameroun) :
- MTN : `67`, `680`–`684`, `650`–`654`
- Orange : `69`, `655`–`659`, `685`–`689`

Le délai de 4 s est réglable : `PAIEMENT_SIMULATION_DELAI_MS` dans `.env`.

---

## 3. Les quatre niveaux d'idempotence

C'est le cœur du module. Un paiement doit pouvoir être rejoué sans jamais
débiter deux fois.

### a) Côté client — en-tête `Idempotency-Key`

Mécanisme identique à celui de Stripe, implémenté dans
[`ServiceIdempotence`](../src/main/java/com/example/E_commerce/Idempotence/ServiceIdempotence.java).

| Situation                              | Réponse                                    |
|----------------------------------------|--------------------------------------------|
| Clé inconnue                           | exécution, réponse mémorisée               |
| Même clé + même corps                  | réponse mémorisée, `Idempotent-Replay: true` |
| Même clé + corps différent             | `409` — la clé a déjà servi                |
| Même clé, traitement encore en cours   | `409` — réessayez dans un instant          |
| Même clé, autre utilisateur            | `409` — cloisonnement par compte           |

La clé est **réservée et committée avant** le traitement métier
(`REQUIRES_NEW` dans [`DepotIdempotence`](../src/main/java/com/example/E_commerce/Idempotence/DepotIdempotence.java)),
sinon deux requêtes simultanées ne se verraient pas l'une l'autre.

Si le traitement échoue, la clé est **libérée** : le client peut rejouer.

### b) Côté métier — une seule tentative active par commande

`PaiementService.initier` renvoie la tentative existante au lieu d'en créer
une seconde :
- commande déjà réglée → on renvoie le paiement réussi, aucun nouveau débit ;
- tentative encore en vol → on renvoie cette tentative ;
- tentative terminée en échec → nouvelle tentative, `tentative + 1`,
  nouvelle référence, l'historique est conservé.

### c) Côté opérateur — la référence interne sert d'identifiant

`PAY-12-1-A1B2C3D4` est transmise à l'opérateur comme identifiant de
transaction. Rejouer l'initiation ne crée pas un second débit :
- en simulation, la passerelle retrouve la transaction et renvoie la même ;
- chez MTN, elle devient le `X-Reference-Id` (via `UUID.nameUUIDFromBytes`,
  donc **déterministe**) et MTN répond `409 Conflict` ;
- chez Orange, elle devient l'`order_id`, qu'Orange refuse de réutiliser.

### d) Côté callback — déduplication + état terminal verrouillé

Trois protections cumulées dans `traiterNotification` :

1. **Contrainte unique** `(fournisseur, id_evenement)` sur la table
   `evenement_paiement` — un callback rejoué 10 fois n'est traité qu'une fois.
2. **Statut terminal non réversible** — un paiement `PAYE` ne peut plus
   devenir `ECHOUE` parce qu'un vieux callback arrive en retard. C'est la
   machine à états de [`StatutPaiement`](../src/main/java/com/example/E_commerce/Enum/StatutPaiement.java)
   qui l'interdit, pas un `if` dispersé dans le code.
3. **Verrou optimiste** (`@Version` sur `Paiement`) — deux callbacks
   simultanés ne peuvent pas s'écraser mutuellement.

Plus un contrôle du montant notifié : s'il diffère du montant de la commande,
le paiement est marqué en échec et l'incident journalisé.

---

## 4. Machine à états

```
         ┌──────────────┐
         │  EN_ATTENTE  │  tentative créée, pas encore transmise
         └──────┬───────┘
                │ initier()
         ┌──────▼───────┐
         │   EN_COURS   │  l'opérateur attend la validation du client
         └──┬───┬───┬───┘
            │   │   │
       ┌────▼┐ ┌▼───────┐ ┌▼───────┐
       │PAYE │ │ECHOUE  │ │EXPIRE  │   états terminaux
       └──┬──┘ └────────┘ └────────┘
          │ remboursement
       ┌──▼───────┐
       │REMBOURSE │
       └──────────┘
```

Conséquences métier, appliquées **uniquement** dans `PaiementService.appliquer` :

| Transition            | Effet                                               |
|-----------------------|-----------------------------------------------------|
| → `PAYE`              | commande `PAYEE`, `datePaiement` renseignée         |
| → `ECHOUE` / `EXPIRE` | commande `ANNULEE`, **stock réservé rendu**         |

Le stock est décrémenté à la création de la commande (réservation) et rendu
si le paiement échoue. Sans cela, un panier abandonné bloquerait le stock
indéfiniment.

---

## 5. Tâches de fond

Aucun opérateur ne garantit la livraison de ses webhooks. Deux jobs
([`OrdonnanceurPaiement`](../src/main/java/com/example/E_commerce/Paiement/Service/OrdonnanceurPaiement.java)) :

| Job              | Période | Rôle                                                  |
|------------------|---------|-------------------------------------------------------|
| Réconciliation   | 30 s    | interroge l'opérateur pour les paiements sans réponse |
| Expiration       | 60 s    | ferme les tentatives dépassant 15 min, rend le stock  |

Ces jobs fonctionnent à l'identique en simulation et en production.

---

## 6. Webhook

```
POST /api/paiements/webhook/MTN_MOMO
Headers : X-Signature: <HMAC-SHA256(corps, PAIEMENT_WEBHOOK_SECRET)>
Body    : {
  "idEvenement": "evt-123",
  "referenceInterne": "PAY-12-1-A1B2C3D4",
  "referenceOperateur": "mtn_momo-abc",
  "statut": "SUCCESSFUL",
  "montant": 51600
}
```

- Route **publique** (l'opérateur n'a pas de session) mais protégée par
  signature HMAC, comparée **à temps constant**.
- Répond toujours `200`, même sur doublon ou référence inconnue : un
  opérateur qui reçoit une erreur rejoue son callback en boucle.
- La passerelle simulée appelle **la même méthode de service** que ce
  contrôleur. Le chemin « notification asynchrone » est donc déjà écrit et
  éprouvé avant le passage en réel.

Tester un callback à la main :

```bash
BODY='{"idEvenement":"evt-manuel-1","referenceInterne":"PAY-12-1-A1B2C3D4","statut":"SUCCESSFUL","montant":51600}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "dev-secret-a-changer-en-production" -hex | awk '{print $2}')
curl -X POST http://localhost:8080/api/paiements/webhook/MTN_MOMO \
     -H "Content-Type: application/json" -H "X-Signature: $SIG" \
     --data-binary "$BODY"
```

---

## 7. Passage aux vraies API

### Ce qu'il y a à faire

**1. Remplir `.env`**

```properties
PAIEMENT_MODE=reel

MTN_MOMO_SUBSCRIPTION_KEY=...
MTN_MOMO_API_USER=...
MTN_MOMO_API_KEY=...
MTN_MOMO_TARGET_ENVIRONMENT=mtncameroon
MTN_MOMO_CALLBACK_URL=https://votre-domaine/api/paiements/webhook/MTN_MOMO

ORANGE_MONEY_CLIENT_ID=...
ORANGE_MONEY_CLIENT_SECRET=...
ORANGE_MONEY_MERCHANT_KEY=...
ORANGE_MONEY_CALLBACK_URL=https://votre-domaine/api/paiements/webhook/ORANGE_MONEY

PAIEMENT_WEBHOOK_SECRET=<secret long et aléatoire>
```

**2. Redémarrer.** `@ConditionalOnProperty` désactive
`MtnMomoSimulee` / `OrangeMoneySimulee` et active `MtnMomoReelle` /
`OrangeMoneyReelle`. Le journal au démarrage le confirme :

```
Passerelles de paiement actives : [MTN_MOMO=MtnMomoReelle, ORANGE_MONEY=OrangeMoneyReelle]
```

**3. Déclarer l'URL de callback** dans les consoles MTN et Orange.

### Ce qui ne change pas

`PaiementService`, les contrôleurs, les entités, les DTO, la machine à
états, l'idempotence, les jobs de fond. Ils ne connaissent que l'interface
[`PasserellePaiement`](../src/main/java/com/example/E_commerce/Paiement/Gateway/PasserellePaiement.java).

### Ce qui reste à finir honnêtement

`MtnMomoReelle` et `OrangeMoneyReelle` sont des **squelettes non testés** :
la structure des appels suit la documentation des opérateurs, mais elle n'a
pas pu être validée faute de clés. Il restera à vérifier, sur les sandbox :

- le mapping des statuts (méthode `traduire` de chaque classe, seul endroit
  à ajuster) ;
- la traduction du format de callback réel vers `NotificationWebhookDto`
  (méthode `traduireStatut` de `WebhookPaiementController`) ;
- la vérification de signature propre à chaque opérateur
  (méthode `estValide` de `VerificateurSignature`) ;
- la mise en cache des jetons OAuth, aujourd'hui redemandés à chaque appel
  (marqué `TODO` dans le code).

Ces quatre points sont localisés dans quatre méthodes, par construction.

---

## 8. Limites connues

- **Un seul nœud.** La passerelle simulée garde ses transactions en mémoire
  et les jobs de fond tournent sur chaque instance. En multi-instance il
  faudrait un verrou distribué (ShedLock) sur les jobs.
- **Pas de remboursement exposé.** Le statut `REMBOURSE` et sa transition
  existent, mais aucun endpoint ne les déclenche.
- **Montants entiers.** `XAF` n'a pas de sous-unité ; `BigDecimal` est
  conservé pour l'exactitude mais l'arrondi n'est pas forcé.
