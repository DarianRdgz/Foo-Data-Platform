package com.fooholdings.fdp.kroger.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fooholdings.fdp.kroger.auth.dto.KrogerTokenResponse;
import com.fooholdings.fdp.kroger.config.KrogerProperties;

@Service
public class KrogerAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KrogerAuthClient.class);

    private final RestClient restClient;
    private final KrogerProperties props;

    public KrogerAuthClient(RestClient.Builder restClientBuilder, KrogerProperties props) {
        this.restClient = restClientBuilder.build();
        this.props = props;
    }

    public KrogerTokenResponse requestClientCredentialsToken() {
        String tokenUrl = props.getOauth().getTokenUrl();
        String scope = props.getOauth().getScope();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
        }

        String authHeader = KrogerAuthHeaderUtil.basicAuthHeaderValue(
                props.getClientId(),
                props.getClientSecret()
        );

        try {
            KrogerTokenResponse response = restClient
                    .post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .body(form)
                    .retrieve()
                    .body(KrogerTokenResponse.class);

            if (response == null || response.getAccessToken() == null || response.getAccessToken().isBlank()) {
                throw new IllegalStateException("Kroger token response missing access_token.");
            }

            log.info("Kroger token retrieved: tokenType={}, expiresIn={}s, scope={}",
                    response.getTokenType(), response.getExpiresIn(), response.getScope());

            return response;

        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            log.error("Kroger token request failed: status={}, statusText={}, body={}",
                    ex.getStatusCode().value(),
                    ex.getStatusText(),
                    body);
            throw ex;
        }
    }
}
