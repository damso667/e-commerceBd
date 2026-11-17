# Déploiement sur Render

| | |
|---|---|
| **Service** | `ecommerce-api` — `srv-dbu5j1cipgvc73aplql0` |
| **URL** | https://ecommerce-api-60sl.onrender.com |
| **Région** | `frankfurt` |
| **Plan** | `free` |
| **Runtime** | Docker (`./Dockerfile`) |
| **Branche** | `main`, déploiement automatique à chaque commit |
| **Dashboard** | https://dashboard.render.com/web/srv-dbu5j1cipgvc73aplql0 |

Base de données : **Supabase** (pooler transaction, `eu-central-1`). Aucune
base Render n'est provisionnée. C'est pourquoi la région choisie est
`frankfurt` et non `oregon` comme les autres services du compte : avec JPA,
une requête HTTP déclenche plusieurs allers-retours SQL, et un aller-retour
Oregon ↔ Francfort coûte ~150 ms chacun.

---

## 1. Pourquoi Docker

Render propose des runtimes natifs pour Node, Python, Go, Ruby, Rust et
Elixir — **pas pour Java**. Il faut donc un `Dockerfile`.

Il est en deux étapes :

- **build** : `maven:3.9-eclipse-temurin-21`, avec le `pom.xml` copié avant
  les sources pour que la couche des dépendances reste en cache ;
- **exécution** : `eclipse-temurin:21-jre-alpine` — JRE seul, pas de JDK ni
  de chaîne Maven dans l'image finale.

Deux détails qui comptent sur le plan gratuit (512 Mo) :

```dockerfile
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
```

On ne tourne pas en root dans un conteneur exposé.

```dockerfile
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC ..."
```

Sans `MaxRAMPercentage`, la JVM dimensionne son tas sur la RAM **de l'hôte**
et non sur la limite du conteneur : l'instance se fait tuer pour dépassement
mémoire avant même de servir une requête.

---

## 2. Adaptations du code

### Le port

```properties
server.port=${PORT:${SERVER_PORT:8080}}
```

Render injecte `PORT` et attend que le processus écoute dessus. L'ordre des
deux replis compte : `PORT` d'abord, `SERVER_PORT` ensuite pour le confort
en local.

### Derrière le proxy

```properties
server.forward-headers-strategy=framework
server.shutdown=graceful
```

Sans la première ligne, l'application se croit en `http://` et génère de
mauvaises URL absolues. La seconde laisse les requêtes en cours se terminer
lors d'un redéploiement.

### La sonde de santé

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
```

`/actuator/health` est ouverte dans `SecurityConfig` et ne renvoie que
`{"status":"UP"}` — aucun détail sur la base ou le disque.

> Le `healthCheckPath` n'est pas réglable via l'API MCP utilisée pour créer
> le service : Render se rabat sur la détection d'écoute du port, ce qui
> fonctionne. Pour une détection de panne plus fine, mettre
> `/actuator/health` dans **Settings → Health Check Path** du dashboard.

---

## 3. Mise en sécurité pour la production

C'est la partie la plus importante, et elle a demandé deux changements.

### Les comptes de démonstration

La même base Supabase a servi en développement. Elle contenait donc
`ralph@gmail.com` avec le rôle `ADMIN` et le mot de passe `password` — ce qui
sur une URL publique est une porte ouverte sur tout le back-office.

`SPRING_PROFILES_ACTIVE=prod` déclenche deux choses :

1. `Donnees` ne crée plus de compte de démonstration (le catalogue, lui,
   continue d'être amorcé) ;
2. `SecurisationProduction` **désactive** les comptes `adrien@gmail.com` et
   `ralph@gmail.com` s'ils existent.

Désactivés et non supprimés : des commandes les référencent, et supprimer
casserait l'historique.

Vérification dans les journaux de démarrage :

```bash
render logs -r srv-dbu5j1cipgvc73aplql0 --text "compte de demonstration" --confirm
```

### Un compte bloqué pouvait encore se connecter

Bug trouvé en préparant le déploiement. `AppUserDetailService` construisait
le `UserDetails` sans le drapeau `disabled` :

```java
.disabled(!personne.isActif())   // ajouté
```

Seul `UtilisateurCourant` vérifiait le champ `actif`, donc tous les endpoints
qui ne l'appellent pas restaient accessibles à un compte bloqué. Sans ce
correctif, désactiver les comptes de démonstration n'aurait rien protégé.

### L'administrateur

Plus de compte de démonstration signifie plus aucun accès au back-office.
`SecurisationProduction` crée donc un administrateur à partir de
`ADMIN_EMAIL` / `ADMIN_PASSWORD`, et met son mot de passe à jour à chaque
démarrage si les variables changent.

Si ces variables sont absentes, **rien n'est créé** et un avertissement est
journalisé. Un mot de passe de moins de 12 caractères est refusé. Un
back-office inaccessible vaut mieux qu'un back-office devinable.

Le mot de passe n'apparaît jamais dans les journaux.

---

## 4. Variables d'environnement

Toutes posées sur le service. Les valeurs sensibles sont lisibles dans
**Dashboard → ecommerce-api → Environment**.

| Variable | Valeur | Note |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | **ne pas retirer** : désactive les comptes de démo |
| `LOG_LEVEL` | `INFO` | `DEBUG` en local |
| `DB_HOST` | `aws-1-eu-central-1.pooler.supabase.com` | pooler Supabase |
| `DB_PORT` | `6543` | mode transaction |
| `DB_NAME` | `postgres` | |
| `DB_USERNAME` | `postgres.<ref>` | |
| `DB_PASSWORD` | *(secret)* | |
| `JPA_DDL_AUTO` | `update` | voir §6 |
| `ADMIN_EMAIL` | `admin@ecommercebd.local` | simple identifiant de connexion |
| `ADMIN_PASSWORD` | *(secret, 28 caractères)* | à lire dans le dashboard |
| `CORS_ALLOWED_ORIGINS` | Vercel + `localhost:4200` | |
| `ADMIN_SEUIL_STOCK_FAIBLE` | `5` | alerte du tableau de bord |
| `PAIEMENT_MODE` | `simulation` | → `reel` le jour J |
| `PAIEMENT_SIMULATION_DELAI_MS` | `4000` | |
| `PAIEMENT_WEBHOOK_SECRET` | *(secret, 64 hex)* | signature HMAC des callbacks |
| `MTN_MOMO_*` | sandbox | clés à remplir |
| `ORANGE_MONEY_*` | sandbox | clés à remplir |
| `*_CALLBACK_URL` | domaine Render | déjà pointées sur le service |

Le fichier `.env` local n'est **pas** dans l'image : il est dans
`.gitignore` et dans `.dockerignore`. En production, `spring.config.import`
est préfixé `optional:`, donc l'application démarre sans lui et lit les
variables d'environnement du serveur.

---

## 5. Vérifier

### Sans identifiants

```bash
python scripts/verifier_deploiement.py
```

Disponibilité, catalogue public, cloisonnement des routes protégées, rejet
d'un callback non signé, format des erreurs. Ce script ne manipule aucun
mot de passe.

### Avec le compte administrateur

À jouer soi-même, après avoir lu `ADMIN_PASSWORD` dans le dashboard :

```bash
curl -c /tmp/j -X POST https://ecommerce-api-60sl.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ecommercebd.local","password":"<le mot de passe>"}'
```

```bash
curl -b /tmp/j https://ecommerce-api-60sl.onrender.com/api/admin/tableau-de-bord
```

### Journaux et déploiements

```bash
render logs -r srv-dbu5j1cipgvc73aplql0 --tail --confirm
```

```bash
render deploys list srv-dbu5j1cipgvc73aplql0 --confirm
```

---

## 6. Limites de cette installation

À connaître avant de considérer que c'est « en production ».

- **Le plan gratuit s'endort, et le réveil est long.** Après 15 minutes sans
  trafic, l'instance est arrêtée. Le démarrage mesuré sur ce service est de
  **~150 secondes** (`Started ECommerceApplication in 146.498 seconds`) :
  512 Mo de RAM, `UseSerialGC`, et Hibernate qui valide le schéma à travers
  le réseau jusqu'à Supabase. La première requête après une mise en veille
  attend donc **plus de deux minutes**, ce qui dépasse le délai d'attente de
  la plupart des clients HTTP.
  Les tâches de fond (réconciliation des paiements, expiration, ramassage des
  commandes abandonnées) **ne tournent pas pendant la veille** : un paiement
  laissé en attente ne sera réconcilié qu'au réveil suivant. Pour un usage
  réel, le plan payant est nécessaire.
- **`JPA_DDL_AUTO=update` n'est pas une stratégie de migration.** Il ajoute
  les colonnes manquantes mais ne modifie ni ne supprime jamais rien. Un
  renommage de champ passera inaperçu et laissera l'ancienne colonne. À
  remplacer par Flyway ou Liquibase avant toute évolution de schéma sérieuse.
- **Supabase gratuit met le projet en pause** après une période
  d'inactivité. L'API renverra alors des erreurs de connexion jusqu'à
  réactivation depuis le dashboard Supabase.
- **Une seule instance.** Les jobs de fond ne sont pas protégés par un
  verrou distribué ; passer à deux instances demanderait ShedLock.
- **Pas de domaine personnalisé ni de HTTPS propre** : l'URL
  `*.onrender.com` est celle fournie par la plateforme.
- **Le mot de passe de base de données a circulé** pendant la mise au point
  de la configuration. Il devrait être renouvelé dans Supabase, puis mis à
  jour dans `DB_PASSWORD` sur Render et dans le `.env` local.

---

## 7. Redéployer

Le déploiement est automatique à chaque commit sur `main`. Manuellement :

```bash
render deploys create srv-dbu5j1cipgvc73aplql0 --confirm
```

Changer une variable d'environnement déclenche aussi un redéploiement.

Pour recréer l'infrastructure ailleurs, le blueprint
[`render.yaml`](../render.yaml) décrit le service à l'identique ; les valeurs
marquées `sync: false` sont à ressaisir.
