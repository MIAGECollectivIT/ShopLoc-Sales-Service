package fr.miage.collectivit.salesservice.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation specifying the roles authorized to invoke an API endpoint. When present on a
 * controller method or class, the security interceptor requires a valid JWT token and verifies that
 * the user's role is among the allowed roles.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
  String[] value();
}
