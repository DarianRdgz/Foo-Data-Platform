package com.fooholdings.fdp.sources.kroger.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.sources.kroger.config.KrogerProperties;

/**
 * Validates that Kroger credentials are configured at application startup.
 *
 * Runs after the context is fully initialized
 *
 * Behavior:
 *   - Missing credentials: logs a WARN but does NOT crash the application.
 *     The platform may intentionally start without Kroger credentials in
 *     environments where only other sources are active.
 *   - Empty credentials (blank string): same — warn only.
 *
 */
@Component
public class KrogerCredsStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(KrogerCredsStartupCheck.class);

    private final KrogerProperties props;

    public KrogerCredsStartupCheck(KrogerProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkCredentials() {
        boolean clientIdMissing = props.getClientId() == null || props.getClientId().isBlank();
        boolean clientSecretMissing = props.getClientSecret() == null || props.getClientSecret().isBlank();

        if (clientIdMissing || clientSecretMissing) {
            log.warn("═══════════════════════════════════════════════════════════");
            log.warn("  Kroger credentials not configured:");
            if (clientIdMissing)     log.warn("    • KROGER_CLIENT_ID is missing or blank");
            if (clientSecretMissing) log.warn("    • KROGER_CLIENT_SECRET is missing or blank");
            log.warn("  Kroger ingestion endpoints will fail until credentials");
            log.warn("  are set in your .env file.");
            log.warn("═══════════════════════════════════════════════════════════");
        } else {
            log.info("Kroger credentials — present (clientId ends with '...{}')",
                    props.getClientId().length() > 4
                            ? props.getClientId().substring(props.getClientId().length() - 4)
                            : "****");
        }
    }
}
