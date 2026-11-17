# 🚀 Collection Bruno - Sales Service API (ShopLoc)

Cette collection de tests fonctionnels et d'intégration HTTP couvre l'intégralité des endpoints du microservice **Sales Service** (Gestion des commandes Click & Collect multi-commerces, passerelle de paiement bancaire et statuts de commande).

---

## 📁 Structure de la Collection

```
bruno/Sales Service/
├── bruno.json
├── environments/
│   ├── Local.bru              # Configuration pour localhost:8083 & Users sur 8081
│   └── Dev.bru                # Configuration pour https://dev.shop-loc.fr
│
├── 00 - Authentification (Users Service)/ # Connexion automatique pour alimenter les tokens
│   ├── 01 - Connexion Client (Pierre) - 200.bru
│   ├── 02 - Connexion Commercant (Suzanne) - 200.bru
│   └── 03 - Connexion Administrateur (Marius) - 200.bru
│
├── 01 - Statut et Sante/       # Diagnostic applicatif et Actuator
│   ├── 01 - Verifier le statut du service (Public) - 200.bru
│   └── 02 - Verifier Actuator Health (Public) - 200.bru
│
├── 02 - Commandes Multi-Commerces (Orders - SLE-186)/ # Cycle de vie des commandes Click & Collect
│   ├── 01 - Creer une commande multi-commerces (Client Authentifie) - 201.bru
│   ├── 02 - Creer une commande multi-commerces (Invite - Sans token) - 201.bru
│   ├── 03 - Consulter une commande par reference - 200.bru
│   ├── 04 - Mettre a jour le statut vers PAID - 200.bru
│   ├── 05 - Mettre a jour le statut vers CANCELLED - 200.bru
│   └── 06 - Consulter une commande inexistante - 404.bru
│
├── 03 - Passerelle de Paiement (Payments - SLE-79 & RG001)/ # Autorisation bancaire émulée
│   ├── 01 - Autoriser un paiement valide (CB Emulee) - 200.bru
│   └── 02 - Consulter une transaction de paiement par reference - 200.bru
│
└── 04 - Securite et Validations/ # Bean Validation et contrôles métier
    ├── 01 - Rejet creation commande panier vide - 400.bru
    ├── 02 - Rejet creation commande prix negatif ou nul - 400.bru
    ├── 03 - Rejet creation commande quantite nulle - 400.bru
    ├── 04 - Rejet creation commande sans storeId - 400.bru
    ├── 05 - Rejet creation commande sans productId - 400.bru
    ├── 06 - Rejet mise a jour statut avec valeur invalide - 400.bru
    └── 07 - Rejet mise a jour statut sur commande inexistante - 404.bru
```

---

## 🛠️ Utilisation dans Bruno

1. Ouvrez l'application **Bruno**.
2. Cliquez sur **Open Collection** et sélectionnez le dossier `bruno/Sales Service`.
3. En haut à droite, sélectionnez l'environnement :
   - `Local` pour pointer vers `http://localhost:8083` (ou `http://localhost:8080` si exécuté directement sans Docker).
   - `Dev` pour pointer vers `https://dev.shop-loc.fr`.
4. Ouvrez le dossier **`00 - Authentification (Users Service)`**, remplacez `USER` et `PASSWORD` par vos identifiants dans la requête choisie (Client, Commerçant ou Admin) et cliquez sur **Send** : le token JWT est **automatiquement injecté** dans les variables d'environnement (`clientToken`, `merchantToken`, `adminToken`).
5. Exécutez la requête `02 - Commandes Multi-Commerces / 01 - Creer une commande multi-commerces` : la variable `orderReference` est **automatiquement capturée** et réinjectée pour les requêtes suivantes de consultation ou de transition de statut.
6. Cliquez sur **Run** sur un dossier pour exécuter toute la suite de tests fonctionnels avec assertions automatisées (`res.status`, `res.body`).

> [!IMPORTANT]
> **Sécurité des identifiants & Git (`skip-worktree`) :**
> Les fichiers de requêtes d'authentification contiennent par défaut `"email": "USER"` et `"password": "PASSWORD"` dans le dépôt distant.
> Afin d'éviter que vos identifiants ou mots de passe de test ne soient accidentellement commités lorsque vous les modifiez dans Bruno, ces fichiers peuvent être marqués avec la directive `skip-worktree` de Git :
> ```bash
> git update-index --skip-worktree "bruno/Sales Service/00 - Authentification (Users Service)/"*
> ```

