package fr.miage.collectivit.salesservice.security;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/** Test RSA keypair and token generation utilities for unit and integration testing. */
public final class TestJwtTokens {

  public static final String TEST_PUBLIC_KEY =
      "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzQ3bLULWQnlNZ7PJDjuquQxRtMdsUCkFa2O5mfMadF1qP7bRwHkq1To1qM6tyULdRFQMLSPH5seqb8A6yQuB8mG4YzVP7ZcQfa7Bq+lmIbVqxWFTfp0eAVTpwkuzFJbGwxvX6MFe0Rla8WTCcE+dqZtWBEepMbb/IKl+rkOtdOP1aepXUsXFl/PZntRPNxHiobL1yfQHDIKoAMPteoW5jnueAwXARB+MnhgCaxcBLmy9W0svw15o5tG50ePq4mtsSipPAIc7hO1JUodyW/ag2qBLRHIL/ixAEe4DiLBc6RdE13AmeRICpfrg7jIfiGTUq8zukPnD/WPx0dCkvBiF1wIDAQAB";

  public static final String TEST_PRIVATE_KEY =
      "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDNDdstQtZCeU1ns8kOO6q5DFG0x2xQKQVrY7mZ8xp0XWo/ttHAeSrVOjWozq3JQt1EVAwtI8fmx6pvwDrJC4HyYbhjNU/tlxB9rsGr6WYhtWrFYVN+nR4BVOnCS7MUlsbDG9fowV7RGVrxZMJwT52pm1YER6kxtv8gqX6uQ6104/Vp6ldSxcWX89me1E83EeKhsvXJ9AcMgqgAw+16hbmOe54DBcBEH4yeGAJrFwEubL1bSy/DXmjm0bnR4+ria2xKKk8AhzuE7UlSh3Jb9qDaoEtEcgv+LEAR7gOIsFzpF0TXcCZ5EgKl+uDuMh+IZNSrzO6Q+cP9Y/HR0KS8GIXXAgMBAAECggEAXHSSbgJ0VA2YmpkEHuBFVmFZF89RJYWRdX3GCkWEGJKkHO1D/upDJ1gzKJVj41q8JJpW0SWQdRxy2ydL+ikvosdv2X+IteJGo7yfg3FdxPYlGbEL8gtKo6QTMDFsoexn20IbJE3IADTT/gKF7xjeyEILUyuyfIF74FC8FzvZoll1AUhTf4g0te0frSOVu88zuwB7+JlTck65tMGHfADUiXvOtBRz3aE2SFXjEjvfgTLw2VTSUZiVEJ9+K8nX9FthXCgpNY2ofGuxOv1eQIGQPvRbJ5rraxsRvX6yxyuen3CzGInATV6PRlIDVTAK9cbxGu1ekXEYv1oXtJU0felH1QKBgQD594yAtinXVvprioKR10CQjQEDPN5B5JPw0a7hRakapxhJODoOjoqzvDNGXhzJ2jAAK+hHRMnRfN3IvwQSeuuAgwXGZEUsvfTvhYBzJAnUOMC32V2+ruv3Ej3XAUHT1tXILXUiVl1yW5qTHqN7sPjtDBy+ziRrtI+xQyJlJUETfQKBgQDSAM7LRKo0tltIVJ3z+fmrkoIFiC7sjCUse4oNyqdy1aje9juWUJhylqrfUYb7Vqi+3QsXDFCjchMJWb26BRj51pxvyJF709RV6exgYCWFXeYxmqq4d6GnnsVbpW8ij454So3fDzNocbJukvmTZUF0erITQpDH5Vkst3FfyS2W4wKBgEjFF2M1JPMqwBZkqWRZvNMYKY5OvwmByMvXz0iHphY7ROez9brdJmXyyR6YMVtWKJk0LxfoX7Sb3FkfusjCfiLYgerir/y5hD/ipqVefXoJ15juL8teUxWHevdHx2krZusyyKHpcKTlJBfmWIOe3qjr2MZ3bsToONjLCY1Wjh5lAoGAcN3cRuVdsTz9LI/F4PXOGUeBuCVsStg7tn+jQCxV+OMagtLGTdB9TsDpj2LK8FSwFvzIVBfbUy0hTBo+XTcWKFkO9QlQH8RGz/IMh/PE1rwT1z8wWDp72JH9snbklyeew8oWG/eYgp7w7nrxxiVam5bCFJXnKHh8iAsD1R5T8U8CgYEAzv1X74cSa2FWwAIxDkzcRCFWmf5wJbYGGLgEplHlMHo37KYEuSOm+jzD00sIfcYNIpUpuu39ecngSALroLnjd2uMeHk0zIDJwVxZdzCy7HLM9H/4HrqahlnWuu34f6IlNP29n3l2Ba6HHavapkL2CKMu/XV3IzAX33eLnNNXrzk=";

  private static final PrivateKey PRIVATE_KEY;
  private static final PublicKey PUBLIC_KEY;

  static {
    try {
      KeyFactory kf = KeyFactory.getInstance("RSA");
      byte[] privBytes = Base64.getDecoder().decode(TEST_PRIVATE_KEY);
      PRIVATE_KEY = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

      byte[] pubBytes = Base64.getDecoder().decode(TEST_PUBLIC_KEY);
      PUBLIC_KEY = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
    } catch (Exception ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  private TestJwtTokens() {}

  public static PrivateKey getPrivateKey() {
    return PRIVATE_KEY;
  }

  public static PublicKey getPublicKey() {
    return PUBLIC_KEY;
  }

  public static String generateToken(
      String role, String email, String userId, String jti, String clientCode) {
    long now = System.currentTimeMillis();
    String actualUserId = userId != null ? userId : UUID.randomUUID().toString();
    String actualJti = jti != null ? jti : UUID.randomUUID().toString();
    String actualEmail = email != null ? email : "client@shoploc.fr";
    String actualClientCode = clientCode != null ? clientCode : "CL10001";

    return Jwts.builder()
        .id(actualJti)
        .subject(actualUserId)
        .issuer("shoploc-auth-service")
        .issuedAt(new Date(now))
        .expiration(new Date(now + 3_600_000L))
        .claim("userId", actualUserId)
        .claim("email", actualEmail)
        .claim("role", role)
        .claim("clientCode", actualClientCode)
        .signWith(PRIVATE_KEY, Jwts.SIG.RS256)
        .compact();
  }
}
