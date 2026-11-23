package fr.miage.collectivit.salesservice.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Point de départ de la tournée Click & Collect")
public record ItineraryOriginResponse(
    String address, Double latitude, Double longitude, boolean customOrigin) {}
