# 🚀 Bruno Collection - Sales Service API (ShopLoc)

This HTTP functional and integration testing collection covers all endpoints of the **Sales Service** microservice (Multi-store Click & Collect order management, bank payment gateway, and order status transitions).

---

## 📁 Collection Structure

```
bruno/Sales Service/
├── bruno.json
├── environments/
│   ├── Local.bru              # Configuration for localhost:8083 & Users on 8081
│   └── Dev.bru                # Configuration for https://dev.shop-loc.fr
│
├── 00 - Authentification (Users Service)/ # Automated login to populate JWT tokens
│   ├── 01 - Connexion Client (Pierre) - 200.bru
│   ├── 02 - Connexion Commercant (Suzanne) - 200.bru
│   └── 03 - Connexion Administrateur (Marius) - 200.bru
│
├── 01 - Statut et Sante/       # Application diagnostics and Actuator health
│   ├── 01 - Verifier le statut du service (Public) - 200.bru
│   └── 02 - Verifier Actuator Health (Public) - 200.bru
│
├── 02 - Commandes Multi-Commerces (Orders - SLE-186)/ # Click & Collect order lifecycle
│   ├── 01 - Creer une commande multi-commerces (Client Authentifie) - 201.bru
│   ├── 02 - Rejet creation commande invite sans token - 401.bru
│   ├── 03 - Consulter une commande par reference - 200.bru
│   ├── 04 - Mettre a jour le statut vers PAID - 200.bru
│   ├── 05 - Mettre a jour le statut vers CANCELLED - 200.bru
│   └── 06 - Consulter une commande inexistante - 404.bru
│
├── 03 - Passerelle de Paiement (Payments - SLE-79 & RG001)/ # Emulated bank authorization
│   ├── 01 - Autoriser un paiement valide (CB Emulee) - 200.bru
│   └── 02 - Consulter une transaction de paiement par reference - 200.bru
│
└── 04 - Securite et Validations/ # Bean Validation and business constraint checks
    ├── 01 - Rejet creation commande panier vide - 400.bru
    ├── 02 - Rejet creation commande prix negatif ou nul - 400.bru
    ├── 03 - Rejet creation commande quantite nulle - 400.bru
    ├── 04 - Rejet creation commande sans storeId - 400.bru
    ├── 05 - Rejet creation commande sans productId - 400.bru
    ├── 06 - Rejet mise a jour statut avec valeur invalide - 400.bru
    ├── 07 - Rejet mise a jour statut sur commande inexistante - 404.bru
    └── 08 - Rejet mise a jour statut par un role CLIENT - 403.bru
```

---

## 🛠️ Usage in Bruno

1. Open the **Bruno** application.
2. Click **Open Collection** and select the `bruno/Sales Service` directory.
3. Select the environment in the top right selector:
   - `Local` targeting `http://localhost:8083` (or `http://localhost:8080` if executed standalone without Docker).
   - `Dev` targeting `https://dev.shop-loc.fr`.
4. Open the **`00 - Authentification (Users Service)`** folder, replace `USER` and `PASSWORD` with your test credentials in the selected request (Client, Merchant, or Admin), and click **Send**: the JWT token is **automatically captured** into environment variables (`clientToken`, `merchantToken`, `adminToken`).
5. Execute the `02 - Commandes Multi-Commerces / 01 - Creer une commande multi-commerces` request: the `orderReference` variable is **automatically captured** and reused across subsequent status update or retrieval queries.
6. Click **Run** on a folder to execute the automated functional test suite with assertions (`res.status`, `res.body`).

> [!IMPORTANT]
> **Credential Security & Git (`skip-worktree`):**
> Authentication request files contain `"email": "USER"` and `"password": "PASSWORD"` by default in the remote repository.
> To prevent your local test credentials or passwords from being accidentally committed when editing them in Bruno, mark these files with Git's `skip-worktree` directive:
> ```bash
> git update-index --skip-worktree "bruno/Sales Service/00 - Authentification (Users Service)/"*
> ```
