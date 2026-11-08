package fr.miage.collectivit.salesservice.bdd.common;

import fr.miage.collectivit.salesservice.client.BankGatewayClient;
import fr.miage.collectivit.salesservice.model.PaymentTransaction;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

public abstract class AbstractScenario {

  protected static final String PAYMENT_AUTHORIZE_ENDPOINT = "/v1/sales/payments/authorize";
  protected static final String PAYMENT_BY_REFERENCE_ENDPOINT = "/v1/sales/payments/{reference}";
  protected static final String BANK_GATEWAY_BASE_URL = "http://localhost:8089";
  protected static final String BANK_GATEWAY_AUTHORIZE_URL =
      BANK_GATEWAY_BASE_URL + BankGatewayClient.AUTHORIZE_PATH;

  protected static final String RG001_STATUS_APPROVED = PaymentTransaction.RG001_APPROVED_STATUS;
  protected static final String RG001_RULE = PaymentTransaction.RG001_RULE_CODE;

  protected static final String HEADER_AUTHORIZATION = "Authorization";
  protected static final String BEARER_PREFIX = "Bearer ";

  protected static final String TEST_JWT_SECRET =
      "dGhpc2lzYXZlcnlzZWNyZXRqd3R1c2Vyc3NlcnZpY2VrZXkxMjM0NTY=";
  protected static final String TEST_JWT_ISSUER = "shoploc-auth-service";
  protected static final String DEFAULT_CLIENT_EMAIL = "client@shoploc.fr";
  protected static final String DEFAULT_CLIENT_CODE = "CL10001";
  protected static final long TOKEN_VALIDITY_MS = 3_600_000L;

  protected static final String MOCK_AUTH_CODE = "AUTH-11111111-2222-3333-4444-555555555555";
  protected static final String MOCK_TIMESTAMP = "2026-11-08T10:30:00Z";

  protected String generateJwtToken(String role) {
    byte[] keyBytes = Base64.getDecoder().decode(TEST_JWT_SECRET);
    SecretKey signingKey = Keys.hmacShaKeyFor(keyBytes);
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject(UUID.randomUUID().toString())
        .issuer(TEST_JWT_ISSUER)
        .issuedAt(new Date(now))
        .expiration(new Date(now + TOKEN_VALIDITY_MS))
        .claim("userId", UUID.randomUUID().toString())
        .claim("email", DEFAULT_CLIENT_EMAIL)
        .claim("role", role)
        .claim("clientCode", DEFAULT_CLIENT_CODE)
        .signWith(signingKey)
        .compact();
  }

  protected String buildBankGatewayApprovedJson() {
    return """
        {
          "status": "%s",
          "authorizationCode": "%s",
          "transactionId": "TXN-%s",
          "amount": 42.50,
          "currency": "EUR",
          "reference": "ORD-BDD-001",
          "timestamp": "%s",
          "rule": "%s"
        }
        """
        .formatted(
            RG001_STATUS_APPROVED, MOCK_AUTH_CODE, UUID.randomUUID(), MOCK_TIMESTAMP, RG001_RULE);
  }
}
