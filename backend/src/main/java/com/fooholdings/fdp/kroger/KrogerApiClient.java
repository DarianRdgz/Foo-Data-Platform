package com.fooholdings.fdp.kroger;

import com.fooholdings.fdp.kroger.config.KrogerProperties;
import com.fooholdings.fdp.kroger.auth.KrogerTokenService;
import com.fooholdings.fdp.kroger.locations.dto.KrogerLocationResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class KrogerApiClient {

    private final RestClient restClient;
    private final KrogerTokenService tokenService;
    private final KrogerProperties props;

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
}
