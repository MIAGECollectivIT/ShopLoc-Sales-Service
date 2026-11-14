@wallet
Feature: Digital Wallet Management (SLE-15, SLE-17, SLE-18)

  Scenario: Consult wallet balance in real time as CLIENT (SLE-17)
    Given an authenticated user with role "CLIENT"
    When the client consults their wallet balance
    Then the HTTP response status should be 200
    And the response JSON field "currency" should equal "EUR"

  Scenario: Reload wallet with valid amount between 5 and 100 EUR (SLE-15)
    Given an authenticated user with role "CLIENT"
    When the client reloads their wallet with amount "25.00" EUR
    Then the HTTP response status should be 200
    And the response JSON field "currency" should equal "EUR"

  Scenario: Reject wallet reload below strict 5.00 EUR floor (SLE-15)
    Given an authenticated user with role "CLIENT"
    When the client reloads their wallet with amount "3.50" EUR
    Then the HTTP response status should be 400

  Scenario: Reject wallet reload above strict 100.00 EUR ceiling (SLE-15)
    Given an authenticated user with role "CLIENT"
    When the client reloads their wallet with amount "150.00" EUR
    Then the HTTP response status should be 400

  Scenario: Debit wallet for in-store purchase with partner store name (SLE-18)
    Given an authenticated user with role "CLIENT"
    When the client reloads their wallet with amount "50.00" EUR
    And the client debits "15.00" EUR from their wallet at store "Boulangerie Le Moulin Doré"
    Then the HTTP response status should be 200
    And the response JSON field "currency" should equal "EUR"

  Scenario: Reject debit when wallet funds are insufficient (SLE-18)
    Given an authenticated user with role "CLIENT"
    When the client debits "9999.00" EUR from their wallet at store "Boulangerie Le Moulin Doré"
    Then the HTTP response status should be 400

  Scenario: Consult wallet transaction history (SLE-18)
    Given an authenticated user with role "CLIENT"
    When the client requests their wallet transaction history
    Then the HTTP response status should be 200

  Scenario: Idempotent replay on duplicate reload request with same key (SLE-15)
    Given an authenticated user with role "CLIENT"
    When the client reloads their wallet with amount "25.00" EUR and idempotency key "bdd-idem-key-1"
    Then the HTTP response status should be 200
    When the client reloads their wallet with amount "25.00" EUR and idempotency key "bdd-idem-key-1"
    Then the HTTP response status should be 200

  Scenario: Reject reload when 7-day rolling limit of 250 EUR is exceeded (SLE-15)
    Given an authenticated user with role "CLIENT"
    When the client reloads their wallet with amount "100.00" EUR
    And the client reloads their wallet with amount "100.00" EUR
    And the client reloads their wallet with amount "50.00" EUR
    And the client reloads their wallet with amount "10.00" EUR
    Then the HTTP response status should be 400

  Scenario: Forbid wallet access for MERCHANT role
    Given an authenticated user with role "MERCHANT"
    When the client consults their wallet balance
    Then the HTTP response status should be 403

  Scenario: Deny unauthenticated wallet access
    Given an unauthenticated user
    When the client consults their wallet balance
    Then the HTTP response status should be 401
