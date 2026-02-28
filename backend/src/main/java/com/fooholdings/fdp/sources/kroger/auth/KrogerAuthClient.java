package com.fooholdings.fdp.sources.kroger.auth;

import com.fooholdings.fdp.sources.kroger.config.KrogerProperties;
import com.fooholdings.fdp.sources.kroger.dto.token.KrogerTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Low-level HTTP client for the Kroger OAuth token endpoint
 *
 * Responsibilities:
 *   - Build and execute the client_credentials grant request
 *   - Return a raw KrogerTokenResponse DTO
 *
 * Token endpoint: POST kroger.oauth.token-url
 * Grant type: client_credentials
 */
@Component
public class KrogerAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KrogerAuthClient.class);

    private final RestClient restClient;
    private final KrogerProperties props;

    public KrogerAuthClient(RestClient.Builder builder, KrogerProperties props) {
        this.props = props;
        // Token endpoint has its own URL
        this.restClient = builder
                .baseUrl(props.getOauth().getTokenUrl())
                .build();
    }

    /**
     * Fetches a new access token from Kroger
     * Throws RestClientException on HTTP errors
     */
    public KrogerTokenResponse fetchToken() {
        log.debug("Requesting Kroger access token (scope={})", props.getOauth().getScope());

        String authHeader = KrogerAuthHeaderUtil.buildBasicAuthHeader(
                props.getClientId(), props.getClientSecret());

        String formBody = "grant_type=client_credentials&scope=" + props.getOauth().getScope();

        KrogerTokenResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", authHeader)
                .body(formBody)
                .retrieve()
                .body(KrogerTokenResponse.class);

        if (response == null || response.getAccessToken() == null) {
            throw new RestClientException("Kroger token response was null or missing access_token");
        }

        log.debug("Kroger token fetched — type={}, expiresIn={}s",
                response.getTokenType(), response.getExpiresIn());
        return response;
    }
}
