Feature: Bank Payment Gateway Emulation and Transaction Audit (SLE-79)

  Scenario: Authorize a valid payment under rule RG001
    Given an authenticated user with role "CLIENT"
    And the external bank gateway approves payments under rule RG001
    When a payment authorization is requested for amount "42.50" currency "EUR" reference "ORD-BDD-001" and token "tok_visa_4242"
    Then the HTTP response status should be 200
    And the response JSON field "status" should equal "TRANSACTION_APPROVED"
    And the response JSON field "rule" should equal "RG001"
    And the response JSON field "reference" should equal "ORD-BDD-001"
    And the response JSON field "authorizationCode" should not be empty
    And the response JSON field "transactionId" should not be empty
    And a payment transaction with reference "ORD-BDD-001" is persisted in database with status "TRANSACTION_APPROVED"

  Scenario: Recover automatically when the external bank gateway fails once with 503
    Given an authenticated user with role "CLIENT"
    And the external bank gateway fails once with 503 before approving on retry
    When a payment authorization is requested for amount "15.00" currency "EUR" reference "PARK-RETRY-002" and token "tok_mastercard_9999"
    Then the HTTP response status should be 200
    And the response JSON field "status" should equal "TRANSACTION_APPROVED"
    And a payment transaction with reference "PARK-RETRY-002" is persisted in database with status "TRANSACTION_APPROVED"

  Scenario: Retrieve an authorized payment transaction by reference
    Given an authenticated user with role "CLIENT"
    And the external bank gateway approves payments under rule RG001
    When a payment authorization is requested for amount "29.90" currency "EUR" reference "ORD-LOOKUP-003" and token "tok_visa_1234"
    Then the HTTP response status should be 200
    When the payment transaction is requested for reference "ORD-LOOKUP-003"
    Then the HTTP response status should be 200
    And the response JSON field "reference" should equal "ORD-LOOKUP-003"
    And the response JSON field "status" should equal "TRANSACTION_APPROVED"

  Scenario: Return 404 Not Found when querying an unknown payment reference
    Given an authenticated user with role "CLIENT"
    When the payment transaction is requested for reference "UNKNOWN-REF-999"
    Then the HTTP response status should be 404

  Scenario: Return 502 Bad Gateway when the external bank gateway is persistently unavailable
    Given an authenticated user with role "CLIENT"
    And the external bank gateway is completely unavailable with 503
    When a payment authorization is requested for amount "50.00" currency "EUR" reference "ORD-FAIL-503" and token "tok_visa_4242"
    Then the HTTP response status should be 502

  Scenario: Deny payment authorization when caller is not authenticated
    Given an unauthenticated user
    When a payment authorization is requested for amount "10.00" currency "EUR" reference "ORD-ANON-001" and token "tok_visa_4242"
    Then the HTTP response status should be 401

  Scenario Outline: Reject malformed payment authorization requests with 400 Bad Request
    Given an authenticated user with role "CLIENT"
    When a payment authorization is requested for amount "<amount>" currency "<currency>" reference "<reference>" and token "<token>"
    Then the HTTP response status should be 400

    Examples:
      | amount | currency | reference   | token         |
      | -10.00 | EUR      | ORD-BAD-001 | tok_visa_4242 |
      | 0.00   | EUR      | ORD-BAD-002 | tok_visa_4242 |
      | 25.00  | EURO     | ORD-BAD-003 | tok_visa_4242 |
      | 25.00  | EUR      |             | tok_visa_4242 |
      | 25.00  | EUR      | ORD-BAD-005 |               |

