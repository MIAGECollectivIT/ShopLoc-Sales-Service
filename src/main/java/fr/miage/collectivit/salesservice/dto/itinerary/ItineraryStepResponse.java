package fr.miage.collectivit.salesservice.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Étape ordonnée d'une tournée de retrait Click & Collect")
public record ItineraryStepResponse(
    int stepNumber,
    UUID storeUid,
    String storeName,
    String address,
    Double latitude,
    Double longitude,
    double distanceFromPreviousKm,
    int travelTimeMinutes,
    Instant estimatedArrivalTime,
    boolean isOpenAtArrival,
    boolean isClosedAllDay,
    boolean isSameLocationAsPrevious,
    String scheduleInfo,
    String warningAlert) {}
