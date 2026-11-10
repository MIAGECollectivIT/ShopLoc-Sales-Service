Feature: Multi-Store Click & Collect Order Creation and Amount Locking (SLE-186)

  Scenario: Successfully create a multi-store click and collect order with calculated totals
    Given an authenticated user with role "CLIENT"
    And a customer creates a multi-store order with 2 items from store 1 at "1.35" EUR and 1 item from store 2 at "22.00" EUR
    Then the HTTP response status should be 201
    And the response JSON field "status" should equal "PENDING_PAYMENT"
    And the response JSON field "currency" should equal "EUR"
    And the response JSON field "totalAmount" should equal "24.7"
    And the response JSON field "orderReference" should not be empty

  Scenario: Reject order creation when item list is empty
    Given an authenticated user with role "CLIENT"
    When an order is created with an empty items list
    Then the HTTP response status should be 400

  Scenario: Reject order creation when item stock is insufficient in Store Service
    Given an authenticated user with role "CLIENT"
    And product 99 in store 1 has insufficient stock
    When an order is created with 1 item of product 99 from store 1 at "10.00" EUR
    Then the HTTP response status should be 400

  Scenario: Reject order creation when unauthenticated
    Given an unauthenticated user
    When an order is created with an empty items list
    Then the HTTP response status should be 401

  Scenario: Update order status to PAID after payment authorization with MERCHANT role
    Given an authenticated user with role "MERCHANT"
    And an existing order with status "PENDING_PAYMENT"
    When the order status is updated to "PAID"
    Then the HTTP response status should be 200
    And the response JSON field "status" should equal "PAID"

  Scenario: Reject order status update when role is CLIENT
    Given an authenticated user with role "CLIENT"
    And an existing order with status "PENDING_PAYMENT"
    When the order status is updated to "PAID"
    Then the HTTP response status should be 403
