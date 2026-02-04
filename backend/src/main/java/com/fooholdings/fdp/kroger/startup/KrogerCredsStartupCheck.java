package com.fooholdings.fdp.kroger.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fooholdings.fdp.kroger.config.KrogerProperties;

@Configuration
public class KrogerCredsStartupCheck {
    private static final Logger log = LoggerFactory.getLogger(KrogerCredsStartupCheck.class);

    @Bean
    ApplicationRunner krogerCredsLogger(KrogerProperties props) {
        return args -> {
            String id = props.getClientId();
            String secret = props.getClientSecret();

            // Fail fast if missing
            if (isBlank(id) || isBlank(secret)) {
                throw new IllegalStateException("Missing Kroger API credentials. Set KROGER_CLIENT_ID and KROGER_CLIENT_SECRET.");
            }

            log.info("Kroger creds loaded (masked): clientId={}, clientSecret={}",
                    mask(id, 4), mask(secret, 4));
        };
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String mask(String value, int keepLast) {
        if (value == null) return "(null)";
        if (value.length() <= keepLast) return "****";
        return "****" + value.substring(value.length() - keepLast);
    }
}
