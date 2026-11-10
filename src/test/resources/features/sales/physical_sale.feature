Feature: In-store Physical Checkout and Loyalty Points Attribution

  Scenario: Record a valid in-store physical checkout with calculated loyalty points
    Given an authenticated user with role "MERCHANT"
    And the store 10 has a loyalty rate of 1.00 EUR for 1.0 PTS
    When an in-store physical sale is recorded for store 10 client code "k7P9xQ2" and amount "25.50"
    Then the HTTP response status should be 201
    And the response JSON field "status" should equal "COMPLETED"
    And the response JSON field "storeId" should equal "10"
    And the response JSON field "clientCode" should equal "K7P9XQ2"
    And the response JSON field "pointsEarned" should equal "25.5"
    And the response JSON field "pointsRateApplied" should equal "1.00 EUR = 1.0 PTS"
    And the response JSON field "transactionReference" should not be empty
    And a physical sale transaction for client "K7P9XQ2" is persisted with points "25.5"

  Scenario: Calculate points accurately with a custom store loyalty rate
    Given an authenticated user with role "MERCHANT"
    And the store 12 has a loyalty rate of 2.00 EUR for 1.0 PTS
    When an in-store physical sale is recorded for store 12 client code "ABC1234" and amount "50.00"
    Then the HTTP response status should be 201
    And the response JSON field "status" should equal "COMPLETED"
    And the response JSON field "pointsEarned" should equal "25.0"
    And the response JSON field "pointsRateApplied" should equal "2.00 EUR = 1.0 PTS"

  Scenario: Fall back gracefully to default loyalty rate when Loyalty Service is unreachable
    Given an authenticated user with role "MERCHANT"
    And the loyalty service is unavailable with 503 for store 10
    When an in-store physical sale is recorded for store 10 client code "k7P9xQ2" and amount "30.00"
    Then the HTTP response status should be 201
    And the response JSON field "status" should equal "COMPLETED"
    And the response JSON field "pointsEarned" should equal "30.0"
    And the response JSON field "pointsRateApplied" should equal "1.00 EUR = 1.0 PTS"

  Scenario: Reject in-store physical sale when client loyalty code is blank
    Given an authenticated user with role "MERCHANT"
    When an in-store physical sale is recorded for store 10 client code "" and amount "15.00"
    Then the HTTP response status should be 400

  Scenario Outline: Reject in-store physical sale when amount is non-positive or has more than 2 decimals
    Given an authenticated user with role "MERCHANT"
    When an in-store physical sale is recorded for store 10 client code "k7P9xQ2" and amount "<amount>"
    Then the HTTP response status should be 400

    Examples:
      | amount  |
      | 0.00    |
      | -10.00  |
      | 15.123  |

  Scenario: Reject in-store physical sale when unauthenticated
    Given an unauthenticated user
    When an in-store physical sale is recorded for store 10 client code "k7P9xQ2" and amount "20.00"
    Then the HTTP response status should be 401

  Scenario: Reject in-store physical sale when user has role CLIENT
    Given an authenticated user with role "CLIENT"
    When an in-store physical sale is recorded for store 10 client code "k7P9xQ2" and amount "20.00"
    Then the HTTP response status should be 403
