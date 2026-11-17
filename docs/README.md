# Documentation ShopLoc Collectiv'IT

Ce dossier regroupe les spécifications d'architecture, les guides techniques et la documentation des fonctionnalités du projet ShopLoc.

---

## Sommaire des Documents

| Document | Description | User Stories |
| :--- | :--- | :--- |
| **[Architecture du Porte-Monnaie Numérique (Wallet)](./architecture_porte_monnaie_wallet.md)** | Détail des mécanismes d'idempotence, isolation des échecs (`REQUIRES_NEW`), verrouillage pessimiste, plafonds glissants RG001, sécurité IDOR et intégration au checkout. | **SLE-15**, **SLE-17**, **SLE-18** |
| **[Architecture Événementielle : Paiements Physiques & Fidélité](./architecture_physical_payment_loyalty_events.md)** | Spécification technique de la publication d'événements Kafka lors des encaissements physiques en boutique, schémas de topic, résilience et intégration avec Loyalty-Service. | **SLE-36**, **SLE-171**, **SLE-172**, **SLE-173**, **SLE-174**, **SLE-175** |

---

*Mis à jour en Novembre 2026.*
