package fr.miage.collectivit.salesservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreDto(Long id, String name, String status) {
  public boolean isRevoked() {
    return "REVOKED".equalsIgnoreCase(status);
  }
}
