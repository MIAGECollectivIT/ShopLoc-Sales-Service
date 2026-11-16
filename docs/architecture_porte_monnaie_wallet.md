# Technical Documentation & Architecture: Digital Citizen Wallet
**User Stories covered:** SLE-15 (Online Card Reload), SLE-17 (Real-Time Balance), SLE-18 (Debit & Audit History)  
**Modules involved:** `ShopLoc-Sales-Service` (Backend Spring Boot) & `ShopLoc-WebClient` (Frontend Astro / React)  
**Date:** November 2026

---

## Table of Contents
1. [Overview & General Architecture](#1-overview--general-architecture)
2. [Idempotency & Replay Protection (`SLE-15`)](#2-idempotency--replay-protection-sle-15)
3. [Failure Isolation & Guaranteed Traceability (`WalletAuditService`)](#3-failure-isolation--guaranteed-traceability-walletauditservice)
4. [Concurrency Prevention & Pessimistic Locking](#4-concurrency-prevention--pessimistic-locking)
5. [Regulatory Limits & Rolling 7-Day Window Calculation](#5-regulatory-limits--rolling-7-day-window-calculation)
6. [Access Control & IDOR Prevention](#6-access-control--idor-prevention)
7. [Wallet Payment Integration in Checkout Workflow](#7-wallet-payment-integration-in-checkout-workflow)
8. [Testing Matrix & Coverage](#8-testing-matrix--coverage)

---

## 1. Overview & General Architecture

The digital citizen wallet enables users (Citizens, Merchants, and Administrators) to hold a digital euro balance, reload it online via bank card through a secure payment gateway, and spend it to settle purchases online (Click & Collect) or in-store.

### Component Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│                   ShopLoc-WebClient                    │
│   (Astro 5 + React 19 + TailwindCSS + Lucide Icons)     │
│  - /wallet : Inquiries, Reloads, Audit History         │
│  - /checkout/:ref : Cart payment with Wallet option     │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP + Bearer JWT
                            ▼
┌────────────────────────────────────────────────────────┐
│                 ShopLoc-Sales-Service                  │
│               (Spring Boot 4 / Java 21)                │
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
│   │     (Bank Gateway)        │      │   Service    │  │
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

## 2. Idempotency & Replay Protection (`SLE-15`)

### Addressed Problem
In financial and monetary credit systems, automated network retries (mobile disconnections, HTTP client timeouts) or inadvertent rapid double-clicks by users can trigger multiple identical requests. Without protection, this would cause duplicate bank charges and duplicate wallet balance credits.

### Implementation Sequence

```
Client (WebClient)              Sales-Service                Sales-DB / Bank
       │                              │                             │
       │── POST /wallet/reload ──────▶│                             │
       │   X-Idempotency-Key: uuid    │── Verify existing key ─────▶│
       │                              │◀─ Not found ────────────────│
       │                              │                             │
       │                              │── Bank gateway call ────────┼──▶ [Bank Gateway]
       │                              │◀─ Authorized (200 OK) ──────┼───
       │                              │                             │
       │                              │── INSERT transaction ──────▶│
       │                              │   (idempotency_key=uuid)    │
       │                              │── UPDATE balance ──────────▶│
       │◀── 200 OK (WalletResponse) ──│                             │
       │                              │                             │
       │   [NETWORK RETRY / DOUBLE-CLICK WITH SAME KEY]            │
       │── POST /wallet/reload ──────▶│                             │
       │   X-Idempotency-Key: uuid    │── Verify existing key ─────▶│
       │                              │◀─ Transaction found! ───────│
       │                              │                             │
       │                              │ [BYPASS RELOAD PROCESSING]  │
       │                              │ No bank gateway call        │
       │                              │ No new balance credit       │
       │◀── 200 OK (Identical result)─│                             │
```

#### Key Implementation Details:
1. **Frontend Key Generation**: If no idempotency key is explicitly supplied, the TypeScript client automatically generates a v4 UUID (`crypto.randomUUID()`) attached to both the `X-Idempotency-Key` HTTP header and the request body.
2. **Database Unique Constraint (Flyway V7)**:
   ```sql
   CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_transactions_user_idempotency
   ON wallet_transactions (user_id, idempotency_key);
   ```
3. **24-Hour TTL Expiration**:
   ```java
   // In WalletService.java
   private static final int IDEMPOTENCY_TTL_HOURS = 24;

   Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
   if (!tx.getCreatedAt().isAfter(cutoff)) {
     throw new IllegalArgumentException(
         "Idempotency key expired (validity: 24 hours). Please retry with a new key.");
   }
   ```
4. **Transparent Replay**: If the key exists and is less than 24 hours old, the system logs the replay event, skips the external bank charge, and returns the current consistent wallet state immediately.

---

## 3. Failure Isolation & Guaranteed Traceability (`WalletAuditService`)

### Addressed Problem
Under standard Spring transactional management (`@Transactional`), if the external bank gateway rejects the authorization (HTTP `502 Bad Gateway` or exception thrown), the active database transaction undergoes a complete rollback. Audit rows written to the database prior to the exception would be reverted, leaving no audit trail for customers or administrators regarding failed reload attempts.

### Solution: Isolated Transaction Propagation (`REQUIRES_NEW`)

The `WalletAuditService` class isolates the audit lifecycle from the business operation lifecycle:

```java
@Service
@RequiredArgsConstructor
public class WalletAuditService {

  private final WalletTransactionRepository walletTransactionRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailedReload(String userId, BigDecimal amount, String failureReason) {
    WalletTransaction failedTx =
        WalletTransaction.builder()
            .userId(userId.trim())
            .transactionReference("WTX-FAIL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT))
            .amount(amount)
            .type(WalletTransactionType.TOPUP)
            .status("FAILED")
            .description("Card reload failed: " + failureReason)
            .balanceAfter(BigDecimal.ZERO)
            .createdAt(Instant.now())
            .build();

    walletTransactionRepository.save(failedTx);
  }
}
```

#### Benefits:
- Even when the primary reload execution throws an exception and its transaction context rolls back, `recordFailedReload` executes in a dedicated physical database connection and commits independently.
- In the frontend UI (`WalletPageView.tsx`), the failed transaction is rendered with a distinct red `FAILED` badge and warning icon.

---

## 4. Concurrency Prevention & Pessimistic Locking

### Addressed Problem
During concurrent debits or credits (e.g. rapid user clicks across tabs or mobile connections), two concurrent threads could read a balance of `50.00 €` simultaneously, debit `30.00 €` each, and persist `20.00 €` instead of rejecting the second operation for insufficient funds (*Lost Update* race condition).

### Solution: Row-Level Pessimistic Write Lock (`PESSIMISTIC_WRITE`)

In `WalletRepository.java`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
Optional<Wallet> findByUserIdForUpdate(@Param("userId") String userId);
```

### Mechanism:
- Generates a PostgreSQL `SELECT ... FOR UPDATE` row lock.
- The first transaction acquires an exclusive row lock on the citizen's wallet.
- Concurrent transactions for the same `userId` block at the database level until the initial transaction executes `COMMIT` or `ROLLBACK`.
- Rolling limit verification and balance mutations strictly operate against freshly locked state.

---

## 5. Regulatory Limits & Rolling 7-Day Window Calculation

In adherence to the **RG001** compliance rules:
- **Minimum amount per reload:** `5.00 €`
- **Maximum amount per reload:** `100.00 €`
- **Rolling 7-day maximum reload limit:** `250.00 €`

### Dynamic Database Calculation
The limit is calculated dynamically over an exact rolling window of **168 hours** (`Instant.now().minus(7, ChronoUnit.DAYS)`), rather than a static calendar week:

```java
// In WalletTransactionRepository.java
@Query("""
    SELECT COALESCE(SUM(t.amount), 0)
    FROM WalletTransaction t
    WHERE t.userId = :userId
      AND t.type = :type
      AND t.status = 'SUCCESS'
      AND t.createdAt >= :since
""")
BigDecimal sumAmountByUserIdAndTypeAndCreatedAtAfter(
    @Param("userId") String userId,
    @Param("type") WalletTransactionType type,
    @Param("since") Instant since);
```

The remaining reload capacity is computed in real time and included in each `WalletResponse` payload via the `weeklyReloadRemaining` field.

---

## 6. Access Control & IDOR Prevention

### Risk Definition (Insecure Direct Object Reference)
A classic vulnerability allows malicious actors to query or mutate another citizen's wallet by altering query parameters (e.g. `GET /v1/sales/wallet?userId=victim-uuid`).

### Security Strategy in `WalletController.java`
Strict user binding based on JWT subject claims:

```java
private String resolveUserId(String requestedUserId, HttpServletRequest request) {
  String authUserId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
  String authRole = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ROLE);

  // Only administrators are permitted to query or operate on behalf of another user ID
  if (UserRole.ADMIN.name().equalsIgnoreCase(authRole)
      && requestedUserId != null
      && !requestedUserId.isBlank()) {
    return requestedUserId.trim();
  }

  // All other users (CLIENT, MERCHANT) are strictly bound to their authenticated token subject
  if (authUserId != null && !authUserId.isBlank()) {
    return authUserId.trim();
  }

  throw new ForbiddenException("Missing authenticated user context");
}
```

---

## 7. Wallet Payment Integration in Checkout Workflow

As part of **SLE-18**, the digital wallet is integrated as a payment method in the Click & Collect checkout funnel (`/checkout/:id`).

### Settlement Lifecycle:
1. **Immediate Eligibility Assessment**:
   - Compares the real-time wallet balance against the total order amount.
   - Sufficient balance: `100% (X.XX €)` button enabled.
   - Insufficient balance: button disabled with `Insufficient funds` prompt and link to `/wallet`.
2. **Atomic Execution**:
   - `POST /v1/sales/wallet/debit` debits the balance under pessimistic write lock and creates an audit transaction.
   - Order transitions to `PAID` via payment orchestration.
   - Retains a digital receipt for pickup (`SLE-21`).

---

## 8. Testing Matrix & Coverage

| Component | Test Type | Validated Scenarios | Status |
| :--- | :--- | :--- | :---: |
| **Idempotency & Replay** | BDD Gherkin / JUnit | Duplicate reload with same key, 200 OK without double charge | ✅ Passed |
| **7-Day Rolling Limit** | BDD Gherkin / JUnit | Rejection when exceeding 250 € over rolling 7 days | ✅ Passed |
| **5 € & 100 € Boundaries** | BDD Gherkin / Unit | Rejection of amounts below 5.00 € or above 100.00 € | ✅ Passed |
| **Audit Failure Traceability**| Integration / Mockito | Gateway error logs FAILED transaction via `REQUIRES_NEW` | ✅ Passed |
| **Pessimistic Locking** | Integration / Service | Prevention of concurrent lost updates and negative balances | ✅ Passed |
| **Security & Roles** | Spring MockMvc | Validated access for `CLIENT`, `MERCHANT`, `ADMIN`; denied for anonymous | ✅ Passed |

---

*Authored for the ShopLoc - Collectiv'IT Engineering Team.*
