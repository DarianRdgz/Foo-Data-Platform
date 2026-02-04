package com.fooholdings.fdp.kroger.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class KrogerAuthHeaderUtil {

    private KrogerAuthHeaderUtil() {}

    public static String basicAuthHeaderValue(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
