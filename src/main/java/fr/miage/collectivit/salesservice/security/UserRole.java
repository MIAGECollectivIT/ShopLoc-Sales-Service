package fr.miage.collectivit.salesservice.security;

/**
 * Standard user roles recognized across the ShopLoc platform. Mirrors the canonical roles emitted
 * in JWT tokens by Users Service.
 */
public enum UserRole {
  CLIENT,
  MERCHANT,
  ADMIN
}
