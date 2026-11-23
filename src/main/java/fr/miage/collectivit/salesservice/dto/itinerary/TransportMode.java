package fr.miage.collectivit.salesservice.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mode de transport utilisé pour le calcul de l'itinéraire Click & Collect")
public enum TransportMode {
  DRIVING,
  WALKING,
  BICYCLE
}
