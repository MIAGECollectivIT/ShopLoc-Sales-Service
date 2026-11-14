# Technical Architecture & Engineering Documentation: Digital Wallet
**Covered User Stories:** SLE-15 (Online Card Reload), SLE-17 (Real-Time Balance), SLE-18 (Debit & Audit Transaction History)  
**Associated Modules:** `ShopLoc-Sales-Service` (Spring Boot 3.4 / Java 21) & `ShopLoc-WebClient` (Astro 5 / React 19)  
**Date:** November 2026

---

## Table of Contents
1. [Overview & High-Level Architecture](#1-overview--high-level-architecture)
2. [Idempotency & Replay Attack Protection (`SLE-15`)](#2-idempotency--replay-attack-protection-sle-15)
3. [Fault Isolation & Guaranteed Audit Trails (`WalletAuditService`)](#3-fault-isolation--guaranteed-audit-trails-walletauditservice)
4. [Concurrency Control & Pessimistic Row Locking](#4-concurrency-control--pessimistic-row-locking)
5. [Regulatory Reload Limits & 7-Day Rolling Window Calculation](#5-regulatory-reload-limits--7-day-rolling-window-calculation)
6. [Access Control & IDOR Prevention](#6-access-control--idor-prevention)
7. [Digital Wallet Payment Integration in Checkout Workflow](#7-digital-wallet-payment-integration-in-checkout-workflow)
8. [Automated Test Suite & Verification Matrix](#8-automated-test-suite--verification-matrix)

---

## 1. Overview & High-Level Architecture

The digital citizen wallet enables authenticated users (`CLIENT`, `MERCHANT`, and `ADMIN`) to maintain an electronic euro balance, recharge funds securely via credit card through a banking gateway, and seamlessly pay for purchases in-store or online via Click & Collect.

### Component Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│                   ShopLoc-WebClient                    │
│   (Astro 5 + React 19 + TailwindCSS + Lucide Icons)     │
│  - /wallet : Balance overview, Top-up, Audit history   │
│  - /checkout/:ref : Multi-store payment with Wallet     │
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
│   │   (Bank Payment Gateway)  │      │   Service    │  │
│   └───────────────────────────┘      └──────────────┘  │
└───────────────────────────┬────────────────────────────┘
                            │ JPA / Hibernate / Flyway
                            ▼
┌────────────────────────────────────────────────────────┐
│                   PostgreSQL Database                  │
│  - wallets (id, user_id, balance, currency)            │
│  - wallet_transactions (tx_ref, amount, type, status,  │
│    idempotency_key, store_name, balance_after)         │
└────────────────────────────────────────────────────────┘
```

---

## 2. Idempotency & Replay Attack Protection (`SLE-15`)

### Problem Statement
In financial transactions, automatic network retries (e.g., mobile connectivity loss, HTTP client timeouts) or accidental user double-clicks can generate multiple duplicate requests. Without protection, this would cause multiple bank card charges and duplicate balance top-ups.

### Sequence Flow

```
Client (WebClient)              Sales-Service                Sales-DB / Bank
       │                              │                             │
       │── POST /wallet/reload ──────▶│                             │
       │   X-Idempotency-Key: uuid    │── Query existing key ──────▶│
       │                              │◀─ Not found ────────────────│
       │                              │                             │
       │                              │── Call bank payment gateway ┼──▶ [Bank Gateway]
       │                              │◀─ Authorized (200 OK) ──────┼───
       │                              │                             │
       │                              │── INSERT transaction ──────▶│
       │                              │   (idempotency_key=uuid)    │
       │                              │── UPDATE balance ──────────▶│
       │◀── 200 OK (WalletResponse) ──│                             │
       │                              │                             │
       │   [NETWORK RETRY / DOUBLE-CLICK WITH SAME IDEMPOTENCY KEY]  │
       │── POST /wallet/reload ──────▶│                             │
       │   X-Idempotency-Key: uuid    │── Query existing key ──────▶│
       │                              │◀─ Key found in DB! ─────────│
       │                              │                             │
       │                              │ [BYPASS RELOAD PROCESS]     │
       │                              │ No bank gateway charge      │
       │                              │ No double credit            │
       │◀── 200 OK (Cached Result) ───│                             │
```

#### Key Implementation Details:
1. **Frontend Key Generation**: If no key is provided, the TypeScript client automatically generates a cryptographic UUID v4 (`crypto.randomUUID()`) attached to the `X-Idempotency-Key` header and request payload.
2. **Database Unique Constraint (Flyway V7)**:
   ```sql
   CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_transactions_user_idempotency
   ON wallet_transactions (user_id, idempotency_key);
   ```
3. **24-Hour Time-To-Live (TTL)**:
   ```java
   private static final long IDEMPOTENCY_TTL_HOURS = 24L;

   Duration age = Duration.between(tx.getCreatedAt(), Instant.now());
   if (age.toHours() >= IDEMPOTENCY_TTL_HOURS) {
     throw new IllegalArgumentException(
         "Idempotency key has expired (validity period: 24 hours). Please generate a new request.");
   }
   ```
4. **Transparent Replay**: If the key exists and is less than 24 hours old, the system logs the replay event, bypasses the external payment gateway, and returns the current consistent wallet state immediately without double charging.

---

## 3. Fault Isolation & Guaranteed Audit Trails (`WalletAuditService`)

### Problem Statement
Under standard Spring `@Transactional` rollbacks, if an external banking gateway rejects a card payment (HTTP 502 / `PaymentGatewayException`), the active database transaction is aborted and rolled back. Any pre-existing log entries in that transaction would disappear, preventing users and administrators from seeing failed top-up records.

### Solution: Independent Transaction Boundary (`REQUIRES_NEW`)

The dedicated `WalletAuditService` bean isolates failure recording from the primary business transaction:

```java
@Service
@RequiredArgsConstructor
public class WalletAuditService {

  private final WalletRepository walletRepository;
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
            .description("Card reload failed: " + failureReason)
            .balanceAfter(wallet.getBalance())
            .idempotencyKey(idempotencyKey)
            .createdAt(Instant.now())
            .build();

    walletTransactionRepository.save(failedTx);
  }
}
```

#### Benefits:
- Even when the outer transaction rolls back upon gateway failure, `recordFailedReload` executes and commits in its own database physical connection.
- In the frontend (`WalletPageView.tsx`), failed attempts are immediately visible with an alert icon, red `FAILED` badge, and strikethrough amount formatted in `Europe/Paris` timezone.

---

## 4. Concurrency Control & Pessimistic Row Locking

### Problem Statement
Concurrent debit or top-up operations (e.g., clicking simultaneously or using multiple tabs) can trigger *lost updates*: two threads read a balance of €50.00, each subtracts €30.00, and save €20.00 instead of rejecting the second attempt for insufficient balance.

### Solution: Pessimistic Write Locking (`PESSIMISTIC_WRITE`)

In `WalletRepository.java`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
Optional<Wallet> findByUserIdForUpdate(@Param("userId") String userId);
```

### Mechanism:
- Issues a SQL `SELECT ... FOR UPDATE` at the PostgreSQL engine level.
- The first transaction acquires an exclusive lock on the wallet row.
- Any concurrent transaction for the same `userId` blocks until the first transaction commits or rolls back.
- Balance recalculation is always performed against freshly locked, verified data.

---

## 5. Regulatory Reload Limits & 7-Day Rolling Window Calculation

In adherence to regulatory standard **RG001**:
- **Minimum amount per reload:** `€5.00`
- **Maximum amount per reload:** `€100.00`
- **7-day rolling window limit:** `€250.00`

### Dynamic Database Aggregation
The rolling limit does not reset on a fixed calendar week (Monday–Sunday), but measures exact **168 hours** back (`Instant.now().minus(7, ChronoUnit.DAYS)`):

```java
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

The remaining reload capacity is returned in `WalletResponse.weeklyReloadRemaining` and displayed as a real-time progress bar on the client dashboard.

---

## 6. Access Control & IDOR Prevention

### Insecure Direct Object Reference (IDOR) Protection
A common vulnerability is manipulating `?userId=someone-else` to inspect or debit another user's balance.

### Controller Resolution Strategy in `WalletController.java`
Strict user binding is enforced:

```java
private String resolveUserId(String requestedUserId, HttpServletRequest request) {
  String authUserId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
  String authRole = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ROLE);

  // Only users with ADMIN role may query or operate on behalf of a third-party userId
  if (UserRole.ADMIN.name().equalsIgnoreCase(authRole)
      && requestedUserId != null
      && !requestedUserId.isBlank()) {
    return requestedUserId.trim();
  }

  // All other users (CLIENT, MERCHANT) are strictly confined to their own JWT subject
  if (authUserId != null && !authUserId.isBlank()) {
    return authUserId.trim();
  }

  throw new ForbiddenException("Missing authenticated user context");
}
```

---

## 7. Digital Wallet Payment Integration in Checkout Workflow

Under **SLE-18**, the digital wallet is directly integrated as a full payment method in the Click & Collect checkout (`/checkout/:id`).

### Checkout Execution Flow:
1. **Real-time Balance Check**:
   - The frontend compares available funds with the order total.
   - If sufficient: Wallet button is selectable (`100% (€X.XX)`).
   - If insufficient: Button is disabled with an alert banner and direct link to reload `/wallet`.
2. **Atomic Execution**:
   - `POST /v1/sales/wallet/debit` with exact amount, merchant partner store details (`storeId`, `storeName`), and `orderReference`.
   - The backend deducts balance under pessimistic row lock and records the `DEBIT` audit entry.
   - Order status transitions to `PAID` via `PATCH /v1/sales/orders/{ref}/status`.
   - Saves transaction authorization receipt in `sessionStorage` (`rule: WALLET_DEBIT`).
   - Redirects to confirmation screen with downloadable official PDF receipt (`SLE-21`).

---

## 8. Automated Test Suite & Verification Matrix

| Tested Area | Test Framework | Validated Scenarios | Result |
| :--- | :--- | :--- | :---: |
| **Idempotency & Replay** | Cucumber / JUnit 5 | Duplicate reload with identical key returns 200 OK without extra charge | ✅ PASS |
| **Idempotency TTL** | JUnit 5 Unit Test | Reject expired idempotency keys older than 24 hours | ✅ PASS |
| **7-Day Rolling Limit** | Cucumber / JUnit 5 | Rejection upon exceeding €250.00 across rolling 168 hours | ✅ PASS |
| **Boundaries (€5 & €100)** | Cucumber / Unit | Strict rejection of invalid amounts (€3.50, €150.00) | ✅ PASS |
| **Audit Failure Logging** | Integration / Mockito | Bank gateway failure produces persisted FAILED transaction record | ✅ PASS |
| **Pessimistic Locking** | Integration Test | Prevent concurrent race conditions and negative balances | ✅ PASS |
| **Role-Based Security** | Spring MockMvc | Allow `CLIENT`, `MERCHANT`, `ADMIN`; deny `GUEST` and unauthenticated callers | ✅ PASS |
| **Frontend Code Quality** | Astro Check / ESLint | Strict TypeScript & React 19 typing, 0 errors, 0 warnings | ✅ PASS |

---

*Authored for the ShopLoc Collectiv'IT Engineering Team.*
