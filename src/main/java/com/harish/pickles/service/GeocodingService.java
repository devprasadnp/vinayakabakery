package com.harish.pickles.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.harish.pickles.config.GoogleMapsProperties;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Google Geocoding API: turns a pin into an address (reverse) and an address into a pin (forward). Used to
 * check that the PIN code and landmark a customer typed agree with the pin they dropped. Every lookup that
 * fails (no key, quota, network, no result) returns empty, so callers can skip the check instead of blocking
 * checkout. The key needs the "Geocoding API" enabled.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    public record Point(double lat, double lng) {
    }

    /** What is at a pin: every PIN code Google lists around it, and a readable address. */
    public record Place(Set<String> postalCodes, String formattedAddress) {
    }

    private final GoogleMapsProperties mapsProperties;
    private final RestClient restClient;

    public GeocodingService(GoogleMapsProperties mapsProperties, RestClient.Builder builder) {
        this.mapsProperties = mapsProperties;
        this.restClient = builder.baseUrl("https://maps.googleapis.com").build();
    }

    /** Both a delivery pin (browser key) and a server-side key are available, so the checks can run. */
    public boolean isConfigured() {
        return mapsProperties.isConfigured() && mapsProperties.isServerConfigured();
    }

    /** Admin "test": asks Google about a well-known place; null when the server key works, else why not. */
    public String probe() {
        if (!mapsProperties.isServerConfigured()) {
            return "No Google Maps key is saved";
        }
        try {
            JsonNode body = restClient.get().uri(u -> u.path("/maps/api/geocode/json")
                    .queryParam("address", "Hyderabad, India").queryParam("key", mapsProperties.effectiveServerKey())
                    .build()).retrieve().body(JsonNode.class);
            String status = body == null ? "no reply" : body.path("status").asText();
            if ("OK".equals(status)) {
                return null;
            }
            String detail = body == null ? "" : body.path("error_message").asText("");
            return "Google answered " + status + (detail.isEmpty() ? "" : ": " + detail);
        } catch (RuntimeException ex) {
            return "Could not reach Google: " + ex.getMessage();
        }
    }

    public Optional<Place> reverse(double lat, double lng) {
        return call(u -> u.queryParam("latlng", lat + "," + lng)).map(body -> {
            Set<String> codes = new LinkedHashSet<>();
            for (JsonNode result : body.path("results")) {
                for (JsonNode comp : result.path("address_components")) {
                    for (JsonNode type : comp.path("types")) {
                        if ("postal_code".equals(type.asText())) {
                            codes.add(comp.path("long_name").asText().replaceAll("\\s", ""));
                        }
                    }
                }
            }
            return new Place(codes, body.path("results").path(0).path("formatted_address").asText(null));
        });
    }

    /** First match for a free-text address, restricted to India. */
    public Optional<Point> locate(String address) {
        return call(u -> u.queryParam("address", address).queryParam("components", "country:IN"))
                .map(body -> body.path("results").path(0).path("geometry").path("location"))
                .filter(loc -> loc.has("lat") && loc.has("lng"))
                .map(loc -> new Point(loc.path("lat").asDouble(), loc.path("lng").asDouble()));
    }

    private Optional<JsonNode> call(java.util.function.Consumer<org.springframework.web.util.UriBuilder> params) {
        if (!mapsProperties.isServerConfigured()) {
            return Optional.empty();
        }
        try {
            JsonNode body = restClient.get().uri(u -> {
                u.path("/maps/api/geocode/json").queryParam("key", mapsProperties.effectiveServerKey());
                params.accept(u);
                return u.build();
            }).retrieve().body(JsonNode.class);
            if (body != null && "OK".equals(body.path("status").asText())) {
                return Optional.of(body);
            }
            log.warn("Geocoding gave no result ({})", body == null ? "empty reply" : body.path("status").asText());
        } catch (RuntimeException ex) {
            log.warn("Geocoding call failed: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    /** Straight-line distance in km between two points. */
    public static double crowKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
