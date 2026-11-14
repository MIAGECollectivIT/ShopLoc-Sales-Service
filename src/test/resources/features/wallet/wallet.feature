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

  Scenario: Consult wallet transaction history (SLE-18)
    Given an authenticated user with role "CLIENT"
    When the client requests their wallet transaction history
    Then the HTTP response status should be 200
