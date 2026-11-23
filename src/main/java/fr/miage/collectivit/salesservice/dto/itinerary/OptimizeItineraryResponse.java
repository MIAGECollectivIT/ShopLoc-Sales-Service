package fr.miage.collectivit.salesservice.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Résultat complet de l'itinéraire optimisé Click & Collect multi-boutiques")
public record OptimizeItineraryResponse(
    ItineraryOriginResponse origin,
    ItineraryStepResponse destination,
    List<ItineraryStepResponse> orderedSteps,
    double totalDistanceKm,
    int estimatedDurationMinutes,
    TransportMode transportMode,
    String googleMapsUrl,
    boolean waypointsTruncated,
    List<String> warnings) {}
