package com.fooholdings.fdp.kroger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fooholdings.fdp.kroger.auth.KrogerTokenService;
import com.fooholdings.fdp.kroger.config.KrogerProperties;
import com.fooholdings.fdp.kroger.locations.dto.KrogerLocationResponse;
import com.fooholdings.fdp.kroger.products.dto.KrogerProductsResponse;

@Service
public class KrogerApiClient {

    private final RestClient restClient;
    private final KrogerTokenService tokenService;
    private final KrogerProperties props;
    private static final Logger log = LoggerFactory.getLogger(KrogerApiClient.class);


    public KrogerApiClient(RestClient.Builder restClientBuilder,
                           KrogerTokenService tokenService,
                           KrogerProperties props) {
        this.restClient = restClientBuilder.build();
        this.tokenService = tokenService;
        this.props = props;
    }

    public KrogerLocationResponse searchLocations(String zipCode, int limit) {
        String token = tokenService.getAccessToken(); // <-- cached + refreshed automatically
        String baseUrl = props.getApi().getBaseUrl();

        return restClient
                .get()
                .uri(baseUrl + "/locations?filter.zipCode=" + zipCode + "&filter.limit=" + limit)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(KrogerLocationResponse.class);
    }

    public KrogerLocationResponse searchLocationsNear(double lat, double lon, int limit) {
        String token = tokenService.getAccessToken();
        String baseUrl = props.getApi().getBaseUrl();

        String uri = baseUrl + "/locations"
                + "?filter.latLong.near=" + lat + "," + lon
                + "&filter.limit=" + limit;

        log.info("Kroger Locations URL: {}", uri);

        return restClient
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(KrogerLocationResponse.class);
    }

    public KrogerProductsResponse searchProducts(String locationId, String term, int limit, int start) {
        String token = tokenService.getAccessToken();
        String baseUrl = props.getApi().getBaseUrl();

        String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8);

        String uri = baseUrl + "/products"
                + "?filter.term=" + encodedTerm
                + "&filter.locationId=" + locationId
                + "&filter.limit=" + limit
                + "&filter.start=" + start;

        return restClient
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(KrogerProductsResponse.class);
    }

}
