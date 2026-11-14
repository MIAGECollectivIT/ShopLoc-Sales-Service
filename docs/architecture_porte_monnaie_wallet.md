# Documentation Technique & Architecture : Porte-Monnaie Numérique (Wallet)
**User Stories couvertes :** SLE-15 (Rechargement en ligne), SLE-17 (Solde temps réel), SLE-18 (Débit & Historique audit)  
**Modules concernés :** `ShopLoc-Sales-Service` (Backend Spring Boot) & `ShopLoc-WebClient` (Frontend Astro / React)  
**Date :** Novembre 2026

---

## Sommaire
1. [Vue d'ensemble & Architecture Générale](#1-vue-densemble--architecture-générale)
2. [Idempotence & Protection Anti-Rejeu (`SLE-15`)](#2-idempotence--protection-anti-rejeu-sle-15)
3. [Isolation des Échecs & Traçabilité Garantie (`WalletAuditService`)](#3-isolation-des-échecs--traçabilité-garantie-walletauditservice)
4. [Prévention de la Concurrence & Verrouillage Pessimiste](#4-prévention-de-la-concurrence--verrouillage-pessimiste)
5. [Plafonds Réglementaires & Calcul Glissant sur 7 Jours](#5-plafonds-réglementaires--calcul-glissant-sur-7-jours)
6. [Contrôle d'Accès & Prévention des Failles IDOR](#6-contrôle-daccès--prévention-des-failles-idor)
7. [Intégration du Paiement en Portefeuille dans le Tunnel d'Achat](#7-intégration-du-paiement-en-portefeuille-dans-le-tunnel-dachat)
8. [Matrice des Tests & Couverture](#8-matrice-des-tests--couverture)

---

## 1. Vue d'ensemble & Architecture Générale

Le système de porte-monnaie numérique citoyen permet aux utilisateurs (Clients, Commerçants et Administrateurs) de détenir un solde dématérialisé en euros, de le recharger par carte bancaire via une passerelle de paiement sécurisée, et de l'utiliser pour régler leurs achats en ligne (Click & Collect) ou en magasin.

### Diagramme d'Architecture des Composants

```
┌────────────────────────────────────────────────────────┐
│                   ShopLoc-WebClient                    │
│   (Astro 5 + React 19 + TailwindCSS + Lucide Icons)     │
│  - /wallet : Consultation, Rechargement, Historique    │
│  - /checkout/:ref : Paiement panier avec option Wallet  │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP + Bearer JWT
                            ▼
┌────────────────────────────────────────────────────────┐
│                 ShopLoc-Sales-Service                  │
│               (Spring Boot 3.4 / Java 21)              │
│                                                        │
│  ┌───────────────────────┐   ┌──────────────────────┐  │
│  │   WalletController    │──▶│    WalletService     │  │
│  └───────────────────────┘   └──────────┬───────────┘  │
│                                         │              │
│                 ┌───────────────────────┴──────────┐   │
│                 │                                  │   │
│                 ▼                                  ▼   │
│   ┌───────────────────────────┐      ┌─────────────┴┐  │
│   │ ExternalMockPaymentClient │      │ WalletAudit  │  │
│   │   (Passerelle Bancaire)   │      │   Service    │  │
│   └───────────────────────────┘      └──────────────┘  │
└───────────────────────────┬────────────────────────────┘
                            │ JPA / Hibernate / Flyway
                            ▼
┌────────────────────────────────────────────────────────┐
│                   PostgreSQL Database                  │
│  - wallets (id, user_id, balance, currency, locks)     │
│  - wallet_transactions (tx_ref, amount, type, status,  │
│    idempotency_key, store_name, balance_after)         │
└────────────────────────────────────────────────────────┘
```

---

## 2. Idempotence & Protection Anti-Rejeu (`SLE-15`)

### Problème adressé
Dans un système de paiement ou de crédit monétaire, les retries automatiques (perte de connexion mobile, timeout client HTTP) ou un double-clic involontaire de l'utilisateur peuvent déclencher plusieurs requêtes identiques. Sans protection, cela entraînerait un débit bancaire multiple et un crédit en double sur le portefeuille.

### Implémentation

```
Client (WebClient)              Sales-Service                Sales-DB / Bank
       │                              │                             │
       │── POST /wallet/reload ──────▶│                             │
       │   X-Idempotency-Key: uuid    │── Vérifie clé existante ───▶│
       │                              │◀─ Non trouvée ──────────────│
       │                              │                             │
       │                              │── Appel passerelle bancaire ┼──▶ [Passerelle]
       │                              │◀─ Accord (200 OK) ──────────┼───
       │                              │                             │
       │                              │── INSERT transaction ──────▶│
       │                              │   (idempotency_key=uuid)    │
       │                              │── UPDATE balance ──────────▶│
       │◀── 200 OK (WalletResponse) ──│                             │
       │                              │                             │
       │   [REJEU RÉSEAU / DOUBLE-CLIC AVEC LA MÊME CLÉ]            │
       │── POST /wallet/reload ──────▶│                             │
       │   X-Idempotency-Key: uuid    │── Vérifie clé existante ───▶│
       │                              │◀─ Transaction trouvée ! ────│
       │                              │                             │
       │                              │ [BYPASS DU RECHARGEMENT]    │
       │                              │ Aucun appel bancaire        │
       │                              │ Aucun nouveau crédit        │
       │◀── 200 OK (Même résultat) ───│                             │
```

#### Points clés du code :
1. **Génération côté Frontend** : Si aucune clé n'est transmise manuellement, le client TypeScript génère automatiquement un UUID v4 (`crypto.randomUUID()`) attaché au header `X-Idempotency-Key` et au corps de la requête.
2. **Contrainte Unique en Base (Flyway V7)** :
   ```sql
   CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_transactions_user_idempotency
   ON wallet_transactions (user_id, idempotency_key);
   ```
3. **TTL de 24 Heures** :
   ```java
   // Dans WalletService.java
   private static final long IDEMPOTENCY_TTL_HOURS = 24L;

   Duration age = Duration.between(tx.getCreatedAt(), Instant.now());
   if (age.toHours() >= IDEMPOTENCY_TTL_HOURS) {
     throw new IllegalArgumentException(
         "La clé d'idempotence a expiré (durée de validité : 24 heures). Veuillez renouveler l'opération.");
   }
   ```
4. **Rejeu transparent** : Si la clé existe et a moins de 24 heures, le système journalise l'événement, ignore l'appel bancaire externe et renvoie directement l'état cohérent du portefeuille.

---

## 3. Isolation des Échecs & Traçabilité Garantie (`WalletAuditService`)

### Problème adressé
Dans le modèle transactionnel standard de Spring (`@Transactional`), si la passerelle bancaire externe rejette la carte (code `502 Bad Gateway` ou exception levée), la transaction courante subit un `rollback` intégral. Les traces écrites en base avant l'exception seraient annulées, empêchant l'utilisateur et l'administrateur de voir l'historique des tentatives infructueuses.

### Solution : Propagation Transactionnelle Isolée (`REQUIRES_NEW`)

La classe `WalletAuditService` a été créée pour séparer le cycle de vie de l'audit de celui de l'opération métier :

```java
@Service
@RequiredArgsConstructor
public class WalletAuditService {

  private final WalletTransactionRepository walletTransactionRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailedReload(
      Wallet wallet,
      String userId,
      BigDecimal amount,
      String failureReason,
      String idempotencyKey) {
    
    WalletTransaction failedTx =
        WalletTransaction.builder()
            .wallet(wallet)
            .userId(userId.trim())
            .transactionReference("WTX-FAIL-" + UUID.randomUUID().toString().substring(0, 8))
            .amount(amount)
            .type(WalletTransactionType.TOPUP)
            .status("FAILED")
            .description("Échec rechargement CB : " + failureReason)
            .balanceAfter(wallet.getBalance())
            .idempotencyKey(idempotencyKey)
            .createdAt(Instant.now())
            .build();

    walletTransactionRepository.save(failedTx);
  }
}
```

#### Avantages :
- Même si la méthode principale de rechargement échoue et que son contexte de persistance est annulé, le commit de `recordFailedReload` est exécuté dans une connexion physique dédiée et persisté immédiatement.
- Côté frontend (`WalletPageView.tsx`), la transaction s'affiche avec un badge rouge `ÉCHEC`, une icône d'alerte et le montant barré.

---

## 4. Prévention de la Concurrence & Verrouillage Pessimiste

### Problème adressé
Lors de débits ou de crédits concurrents (ex: un utilisateur cliquant rapidement ou utilisant deux onglets simultanément), deux threads peuvent lire simultanément un solde de `50,00 €`, débiter chacun `30,00 €`, et enregistrer `20,00 €` au lieu de rejeter la seconde opération pour solde insuffisant (*Lost Update*).

### Solution : Verrouillage Pessimiste en Écriture (`PESSIMISTIC_WRITE`)

Dans `WalletRepository.java` :
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
Optional<Wallet> findByUserIdForUpdate(@Param("userId") String userId);
```

### Mécanisme :
- L'appel génère une clause SQL `SELECT ... FOR UPDATE` au niveau du moteur PostgreSQL.
- La première transaction pose un verrou exclusif sur la ligne du portefeuille.
- Toute transaction concurrente pour le même `userId` est mise en attente au niveau de la base de données jusqu'à ce que la première transaction ait validé (`COMMIT`) ou annulé (`ROLLBACK`).
- Le calcul du nouveau solde s'effectue toujours à partir de la valeur fraîchement mise à jour.

---

## 5. Plafonds Réglementaires & Calcul Glissant sur 7 Jours

Conformément à la règle de conformité **RG001** :
- **Montant minimum par rechargement :** `5,00 €`
- **Montant maximum par rechargement :** `100,00 €`
- **Plafond glissant sur 7 jours :** `250,00 €`

### Calcul dynamique en Base de Données
Le calcul ne s'appuie pas sur une semaine calendaire fixe (du lundi au dimanche), mais sur une fenêtre glissante exacte de **168 heures** (`Instant.now().minus(7, ChronoUnit.DAYS)`) :

```java
// Dans WalletTransactionRepository.java
@Query("""
  SELECT COALESCE(SUM(t.amount), 0)
  FROM WalletTransaction t
  WHERE t.userId = :userId
    AND t.type = fr.miage.collectivit.salesservice.model.WalletTransactionType.TOPUP
    AND t.status = 'SUCCESS'
    AND t.createdAt >= :since
""")
BigDecimal calculateTotalReloadsSince(
    @Param("userId") String userId,
    @Param("since") Instant since);
```

Le solde disponible restant est calculé en temps réel et renvoyé dans chaque payload `WalletResponse` sous le champ `weeklyReloadRemaining`. Le frontend l'affiche sous forme de barre de progression intuitive.

---

## 6. Contrôle d'Accès & Prévention des Failles IDOR

### Définition du risque (IDOR - Insecure Direct Object Reference)
Une faille classique consisterait pour un utilisateur malveillant à appeler l'API en injectant le `userId` d'un autre citoyen dans les paramètres (ex: `GET /v1/sales/wallet?userId=autre-guid`) pour consulter ou manipuler son solde.

### Stratégie de Sécurité dans `WalletController.java`
Le contrôleur applique un principe d'étanchéité stricte :

```java
private String resolveUserId(String requestedUserId, HttpServletRequest request) {
  String authUserId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
  String authRole = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ROLE);

  // Seul le rôle ADMIN est autorisé à cibler un userId tiers
  if (UserRole.ADMIN.name().equalsIgnoreCase(authRole)
      && requestedUserId != null
      && !requestedUserId.isBlank()) {
    return requestedUserId.trim();
  }

  // Tous les autres utilisateurs (CLIENT, MERCHANT) sont strictement bornés à leur propre identité JWT
  if (authUserId != null && !authUserId.isBlank()) {
    return authUserId.trim();
  }

  throw new ForbiddenException("Contexte utilisateur authentifié manquant");
}
```

---

## 7. Intégration du Paiement en Portefeuille dans le Tunnel d'Achat

Dans le cadre de **SLE-18**, le porte-monnaie virtuel est intégré comme moyen de paiement autonome dans le tunnel de checkout Click & Collect (`/checkout/:id`).

### Déroulement du Règlement :
1. **Contrôle d'éligibilité immédiat** :
   - Le frontend compare le solde disponible du portefeuille avec le montant total de la commande.
   - Si le solde est suffisant : bouton activé `100% (X,XX €)`.
   - Si le solde est insuffisant : bouton désactivé avec message `Solde insuffisant` et lien de redirection rapide vers `/wallet` pour recharger.
2. **Exécution atomique** :
   - Appel API `POST /v1/sales/wallet/debit` avec le montant exact, l'ID et nom du commerce, et la référence de commande.
   - Le backend débite le solde sous verrou pessimiste et insère la transaction d'audit `DEBIT`.
   - Appel de validation du statut de commande `PATCH /v1/sales/orders/{ref}/status` avec `PAID`.
   - Stockage en `sessionStorage` d'un reçu avec méthode `WALLET`.
   - Redirection vers l'écran de confirmation et téléchargement du bon de retrait PDF (`SLE-21`).

---

## 8. Matrice des Tests & Couverture

| Composant testé | Type de test | Scénarios validés | Statut |
| :--- | :--- | :--- | :---: |
| **Idempotence & Rejeu** | BDD Gherkin / JUnit | Double rechargement avec même clé, validation 200 OK sans surcoût | ✅ Passé |
| **Plafond 7 jours** | BDD Gherkin / JUnit | Rejet au-delà de 250 € sur fenêtre glissante | ✅ Passé |
| **Bornes 5 € et 100 €** | BDD Gherkin / Unit | Rejet des montants 3,50 € et 150,00 € | ✅ Passé |
| **Audit des Échecs** | Intégration | Simulation refus passerelle -> Transaction FAILED enregistrée | ✅ Passé |
| **Pessimistic Locking** | Intégration / Service | Prévention des débits concurrents avec solde négatif | ✅ Passé |
| **Sécurité & Rôles** | Spring MockMvc | Accès autorisé pour `CLIENT`, `MERCHANT`, `ADMIN`, refusé pour `GUEST` et non-authentifiés | ✅ Passé |
| **Frontend & UI** | Astro Check / ESLint | Typage React 19 / Astro strict, 0 erreurs, 0 avertissements | ✅ Passé |

---

*Document rédigé pour l'équipe ShopLoc - Collectiv'IT.*
