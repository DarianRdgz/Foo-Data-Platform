package com.fooholdings.fdp.sources.kroger.auth;

import java.util.Base64;

/**
 * Utility for building HTTP Basic Authentication headers for Kroger OAuth token requests
 *
 * The Kroger token endpoint requires credentials encoded as:
 *   Authorization: Basic Base64(clientId:clientSecret)
 *
 */
public final class KrogerAuthHeaderUtil {

    private KrogerAuthHeaderUtil() {}

    /**
     * Builds the Authorization header value for the Kroger token endpoint.
     *
     * @param clientId     Kroger API client ID
     * @param clientSecret Kroger API client secret
     * @return header value in the form "Basic <base64 encoded credentials>"
     */
    public static String buildBasicAuthHeader(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }
}
