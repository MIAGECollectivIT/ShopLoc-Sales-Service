package fr.miage.collectivit.salesservice.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Paramètres de calcul d'itinéraire pour une commande")
public record OrderItineraryRequest(
    @Schema(description = "Latitude du point de départ du client", example = "50.62925")
        Double originLatitude,
    @Schema(description = "Longitude du point de départ du client", example = "3.057256")
        Double originLongitude,
    @Schema(
            description = "Adresse textuelle facultative du point de départ",
            example = "Grand Place, 59000 Lille")
        String originAddress,
    @Schema(description = "Mode de transport choisi", defaultValue = "DRIVING")
        TransportMode transportMode,
    @Schema(description = "Heure de départ souhaitée") Instant departureTime) {

  public OrderItineraryRequest {
    if (transportMode == null) {
      transportMode = TransportMode.DRIVING;
    }
  }
}
