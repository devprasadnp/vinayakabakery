package com.harish.pickles.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.harish.pickles.config.GoogleMapsProperties;
import com.harish.pickles.service.StoreLocationService.StoreLocation;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Distance from the store's pin to a delivery pin. Asks Google's Distance Matrix API for the real
 * driving distance; if no key is set, the call fails, or no route exists, falls back to the
 * straight-line (haversine) distance stretched by a road factor so pricing never breaks checkout.
 * The key needs the "Distance Matrix API" enabled besides the Maps JavaScript API.
 */
@Service
public class DistanceService {

    private static final Logger log = LoggerFactory.getLogger(DistanceService.class);
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final GoogleMapsProperties mapsProperties;
    private final StoreLocationService storeLocationService;
    private final DeliveryMapSettingsService mapSettings;
    private final RestClient restClient;

    public DistanceService(GoogleMapsProperties mapsProperties, StoreLocationService storeLocationService,
                           DeliveryMapSettingsService mapSettings, RestClient.Builder builder) {
        this.mapSettings = mapSettings;
        this.mapsProperties = mapsProperties;
        this.storeLocationService = storeLocationService;
        this.restClient = builder.baseUrl("https://maps.googleapis.com").build();
    }

    /** Driving km from the store to the pin, rounded to 0.1; empty if the store or pin is not set. */
    public Optional<Double> distanceKm(Double lat, Double lng) {
        StoreLocation store = storeLocationService.get();
        if (lat == null || lng == null || !store.hasPin()) {
            return Optional.empty();
        }
        var rules = mapSettings.get();
        double km = rules.drivingDistance() && mapsProperties.isServerConfigured()
                ? google(store, lat, lng).orElseGet(() -> straightLine(store, lat, lng, rules.roadFactor()))
                : straightLine(store, lat, lng, rules.roadFactor());
        return Optional.of(Math.round(km * 10) / 10.0);
    }

    private Optional<Double> google(StoreLocation store, double lat, double lng) {
        try {
            JsonNode body = restClient.get()
                    .uri(u -> u.path("/maps/api/distancematrix/json")
                            .queryParam("origins", store.latitude() + "," + store.longitude())
                            .queryParam("destinations", lat + "," + lng)
                            .queryParam("units", "metric")
                            .queryParam("key", mapsProperties.effectiveServerKey()).build())
                    .retrieve().body(JsonNode.class);
            JsonNode element = body == null ? null : body.path("rows").path(0).path("elements").path(0);
            if (element != null && "OK".equals(element.path("status").asText())) {
                return Optional.of(element.path("distance").path("value").asDouble() / 1000.0);
            }
            log.warn("Distance Matrix gave no route ({}); using straight-line distance",
                    body == null ? "empty reply" : body.path("status").asText());
        } catch (RuntimeException ex) {
            log.warn("Distance Matrix call failed ({}); using straight-line distance", ex.getMessage());
        }
        return Optional.empty();
    }

    static double straightLine(StoreLocation store, double lat, double lng, double roadFactor) {
        double dLat = Math.toRadians(lat - store.latitude());
        double dLng = Math.toRadians(lng - store.longitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(store.latitude())) * Math.cos(Math.toRadians(lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * roadFactor;
    }
}
