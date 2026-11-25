# AUDIT_REPORT — ShopLoc-Sales-Service (`Sales`)

> Audit en lecture seule. Aucun fichier source modifié. Chemins relatifs à `src/main/java/fr/miage/collectivit/salesservice/` (noté `…/`).

## 1. Carte d'identité & Baseline

| Élément | Valeur |
|---|---|
| Build | Maven (`mvnw`), Java 21, Spring Boot **4.1.1** |
| Persistance | PostgreSQL + JPA, Flyway (V1→V10, versions uniques ✅) |
| Kafka | `spring-kafka` — **producteur uniquement** (3 publishers, aucun `@KafkaListener`) |
| Sécurité | JWT RS256 **ou HMAC** (`security/JwtKeyProvider.java`), annotation `@RequiresRole` |
| Clients HTTP | `RestClient` : `StoreClient`, `LoyaltyRateClient`, `BankGatewayClient` |
| Autres | OpenPDF (reçus), Mail, ZXing, springdoc, actuator (probes), Cucumber, PMD/Spotless/JaCoCo |
| Taille | ~6 100 lignes Java |
| Compilation | `./mvnw test-compile` : ✅ OK |
| Démarrage | **Non mesuré** (PostgreSQL + Kafka requis) ; analyse statique ci-dessous |

## 2. Problèmes détectés

### 🔴 Critique

**C1 — Perte silencieuse d'événements Kafka (pas d'outbox, erreurs avalées)**
- `…/event/TransactionEventPublisher.java:41-55` : `kafkaTemplate.send(...)` **asynchrone non attendu**, `catch (Exception)` → simple `log.warn`. Même schéma : `…/event/PhysicalPaymentEventPublisher.java:67-80`, `…/event/OrderPaymentEventPublisher.java:71-95`.
- Le paiement est validé et commité en base, mais l'événement vers `Loyalty` (points de fidélité) peut ne jamais partir (broker indisponible, sérialisation) → **incohérence Sales/Loyalty irrécupérable** (« dual write »).
- De plus, `KafkaTemplate` injecté en `@Autowired(required = false)` : s'il est absent, l'événement est simplement ignoré (`…/event/*Publisher.java:35-45`).
- Proposition : pattern **Transactional Outbox** (table `outbox_events` écrite dans la même transaction + relais) ou a minima `send(...).whenComplete(...)` avec métrique/compensation et publication après commit (`@TransactionalEventListener(AFTER_COMMIT)`).
```java
// Avant
kafkaTemplate.send(topic, event.userId(), event);   // fire-and-forget, catch→warn
// Après
kafkaTemplate.send(topic, key, event).whenComplete((r, ex) -> { if (ex != null) outboxRepository.markFailed(event.id(), ex); });
```

**C2 — Fallback de signature JWT HMAC (`JWT_SECRET`) en plus de RSA**
- `…/security/JwtKeyProvider.java:52-71`, `…/security/ServiceTokenProvider.java:31,80`, `application.yaml` (`application.security.jwt.secret-key: "${JWT_SECRET:}"`).
- Deux modes de validation cohabitent (RSA et HMAC) → surface d'attaque (algorithm confusion) et secret symétrique partagé à gérer. Aligner sur RS256 uniquement (le service User signe en RS256).

### 🟠 Majeur

**M1 — Pas de DLT / gestion d'erreurs Kafka explicite ; démarrage Kafka**
- `…/config/KafkaTopicConfig.java:25-60` : beans `NewTopic` + `KafkaAdmin` avec `setFatalIfBrokerNotAvailable(false)` ✅ (démarrage non bloquant) mais `auto-create` par défaut `true` (`:27`), réplication/partitions par défaut (`DEFAULT_REPLICATION_FACTOR`) à valider en prod. Aucune configuration `acks`/`retries`/`delivery.timeout.ms` explicite en dehors de `acks: all` + `enable.idempotence: true` (`application.yaml`, ✅ bonne base).
- Aucun consommateur côté Sales → `ErrorHandlingDeserializer`/`DefaultErrorHandler`/DLT non applicables ici (à vérifier côté `Loyalty`).

**M2 — Listes sans pagination (historiques financiers)**
- `…/controller/WalletController.java:92-107` (`GET /transactions`), `…/controller/OrderController.java:84-97` (`GET /orders`), `…/controller/PaymentController.java:86-103` (`/users/{userId}/transactions`) → croissance non bornée.
- Proposition : `Pageable` + `PageResponse`, tri par date.

**M3 — Gestion d'erreurs non conforme RFC 7807**
- `…/exception/GlobalExceptionHandler.java:16-60` : `Map<String,Object>` artisanales (0 occurrence de `ProblemDetail`).

**M4 — Cohérence transactionnelle : `@Transactional` partiel**
- `…/service/PaymentService.java` : seules des méthodes `readOnly = true` annotées (`:107,115,137`) ; vérifier que l'autorisation de paiement (`authorize`) est bien atomique avec la persistance (un seul `@Transactional` visible dans `PhysicalSaleService.java:63`).
- Positif : verrou pessimiste du wallet (`repository/WalletRepository.java:16`, `@Lock(PESSIMISTIC_WRITE)`) et clé d'idempotence (migration V7) ✅. Pas de `@Version` (verrouillage optimiste) sur `Order`/`PaymentTransaction`.

**M5 — Dockerfile & démarrage non optimisés**
- `Dockerfile` : pas d'AOT/AppCDS ; `application.yaml` : pas de `spring.datasource.hikari.*`, ni `hibernate.temp.use_jdbc_metadata_defaults=false` (dialecte pourtant déclaré), pas de `lazy-initialization`.
- Positif : `ddl-auto: validate`, `open-in-view: false`.
- `management.endpoint.health.show-details: always` expose les détails (DB, Kafka) sans authentification.

**M6 — Pas de traçabilité distribuée**
- Pas de `micrometer-tracing` alors que Sales appelle Store, Loyalty, la passerelle bancaire et publie sur Kafka : propager `traceparent` aussi dans les headers Kafka (`spring.kafka.template.observation-enabled=true`).

**M7 — Endpoints non homogènes**
- `…/controller/PaymentController.java:32` : `@RequestMapping({"/v1/sales/payments", "/api/v1/payments"})` (deux préfixes, seul service Sales à le faire) alors que les autres sont `/v1/sales/...`. Verbe d'action dans l'URL (`/authorize`, `/reload`, `/debit`).

### 🟡 Mineur / Ergonomie

**m1 — `@Autowired`** (principalement sur constructeurs, avec doubles constructeurs et `required=false`) : `…/service/receipt/ReceiptEmailService.java:44`, `…/service/PhysicalSaleService.java:51-56`, `…/service/PaymentService.java:37-41`, `…/event/*Publisher.java:17-26`. Remplacer `@Autowired(required=false)` par `ObjectProvider<KafkaTemplate<…>>` ou profil conditionnel `@ConditionalOnBean`.

**m2 — `@Value` dispersés** (`StoreClient.java`, `ServiceTokenProvider.java:31`, `JwtKeyProvider.java:52`, `KafkaTopicConfig.java`, publishers, …) → `@ConfigurationProperties` typées (`services.*`, `application.kafka.topics.*`).

**m3 — Dépendances (`dependency:analyze`)**
- « Used undeclared » à déclarer explicitement : `org.apache.kafka:kafka-clients`, `org.flywaydb:flyway-core`, `spring-data-jpa`. Les « unused » sont des starters/runtime (faux positifs). `spring-boot-starter-mail` (reçus email) et `openpdf`, `zxing` bien utilisés. springdoc 2.8.5 à valider avec Boot 4.1.
- `JWT_SECRET`/`private-key` déclarés dans le service qui ne signe pas les jetons utilisateurs (`application.yaml` `jwt.private-key`) → à retirer si inutile.

**m4 — `catch (Exception)` génériques (loggés, jamais vides)** : `…/service/WalletAuditService.java:99`, `…/service/receipt/ReceiptPdfService.java:103,206`, `…/service/receipt/ReceiptEmailService.java:91,190`. Typer (`MessagingException`, `DocumentException`).

**m5 — Points conformes ✅** : entités JPA non exposées par les contrôleurs (DTO `*Response`), associations `@ManyToOne` en `LAZY` ; `@OneToMany` par défaut `LAZY` (`Order.java:76`, `Wallet.java:57`, `SaleTransaction.java:87`) — vérifier l'absence de N+1 dans les mappers de réponse (`OrderResponse`) ; timeouts présents sur `StoreClient` (3 s/5 s, `:56-58`), `LoyaltyRateClient` (3 s/5 s), `BankGatewayClient` (5 s/10 s) ; pas de secrets en dur (hors défauts `devpassword` DB) ; `@Valid` sur la majorité des POST (absent de `SalesStatusController`, sans corps).

## 3. Plan d'action priorisé

| # | Action | Gain | Effort |
|---|---|---|---|
| 1 | Outbox transactionnelle / publication after-commit + suivi des échecs d'envoi (C1) | Cohérence Sales↔Loyalty garantie | Élevé |
| 2 | Supprimer le mode HMAC `JWT_SECRET` (C2) | Réduit surface JWT | Faible |
| 3 | Paginer wallet/orders/payments (M2) | Scalabilité | Moyen |
| 4 | `ProblemDetail` (M3) | Cohérence API | Faible |
| 5 | Vérifier/étendre `@Transactional` + `@Version` (M4) | Intégrité financière | Moyen |
| 6 | AOT + AppCDS + Hikari + metadata JDBC (M5) | −30-40 % démarrage | Moyen |
| 7 | `micrometer-tracing` + observation Kafka (M6) | Traçabilité bout en bout | Faible |
| 8 | `show-details=when-authorized` (M5) | Moins d'info exposée | Faible |
| 9 | Unifier préfixes d'URL (M7) | API cohérente | Moyen |
| 10 | `ObjectProvider` / `@ConfigurationProperties` (m1, m2) | Maintenabilité | Moyen |
| 11 | Déclarer `kafka-clients`, `flyway-core` (m3) | Build propre | Faible |


## 4. Annexe — Build Docker (mesuré, build à froid `--no-cache`)

| Étape | Durée |
|---|---|
| `RUN mvn dependency:go-offline -B` (builder 4/6) | **51.0 s** (≈ 1587 artefacts téléchargés) |
| `RUN mvn clean package -DskipTests` (builder 6/6) | 4.6 s |
| Copie JAR + image finale | ~2 s (image 472 Mo) |
| **Total** | **59 s** |

- 🟠 **D1 — `dependency:go-offline` re-télécharge tout à chaque invalidation du cache** (`Dockerfile`, `RUN mvn dependency:go-offline -B`) : il ramène aussi des plugins/dépendances jamais utilisés au build. Aucun cache Maven persistant entre builds. Le cache de couche saute dès que `pom.xml` change, ou après `--no-cache` / prune.
- Proposition (gain estimé : ~50-70 s → ~5-10 s sur rebuild après modif du `pom.xml`) :
```dockerfile
# syntax=docker/dockerfile:1.7
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:resolve-plugins dependency:resolve
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -o clean package -DskipTests
```
- `mvnw`/`.mvn` copiés dans le contexte mais non utilisés : le conteneur utilise l'image `maven:3.9` (OK, mais version Maven non épinglée au wrapper).
- Compilation seule : ~4 s → le build applicatif n'est pas le problème, seul le téléchargement des dépendances l'est.
