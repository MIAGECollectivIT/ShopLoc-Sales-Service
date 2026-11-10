package fr.miage.collectivit.salesservice.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation specifying the user roles authorized to invoke an API endpoint. Placed directly on
 * controller methods, the security interceptor requires a valid JWT token and verifies that the
 * caller's role matches one of the allowed roles.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
  UserRole[] value();
}
