# ShopLoc Collectiv'IT Documentation

This directory centralizes architecture specifications, technical guides, and feature documentation for the ShopLoc Sales Service.

---

## Documentation Index

| Document | Description | User Stories |
| :--- | :--- | :--- |
| **[Digital Citizen Wallet Architecture](./architecture_porte_monnaie_wallet.md)** | Details idempotency mechanisms, failure isolation (`REQUIRES_NEW`), pessimistic locking, RG001 rolling limits, IDOR prevention, and checkout integration. | **SLE-15**, **SLE-17**, **SLE-18** |
| **[In-Store Physical Checkout & Loyalty Points Event-Driven Architecture](./architecture_physical_payment_loyalty_events.md)** | Technical specification for asynchronous Kafka event publishing on physical in-store checkout, topic schemas, error handling, idempotency, and integration with Loyalty-Service. | **SLE-36**, **SLE-171**, **SLE-172**, **SLE-173**, **SLE-174**, **SLE-175** |

---

*Updated November 2026.*
