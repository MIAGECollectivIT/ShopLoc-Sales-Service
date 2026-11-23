package fr.miage.collectivit.salesservice.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Requête d'optimisation d'itinéraire Click & Collect multi-boutiques")
public record OptimizeItineraryRequest(
    List<UUID> storeUids,
    Double originLatitude,
    Double originLongitude,
    String originAddress,
    TransportMode transportMode,
    Instant departureTime) {}
