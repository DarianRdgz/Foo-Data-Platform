package com.fooholdings.fdp.sources.kroger.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.sources.kroger.auth.KrogerTokenService;
import com.fooholdings.fdp.sources.kroger.auth.KrogerTokenState;

/**
 * Diagnostic endpoints for the Kroger OAuth token
 *
 * Not part of any external API contract
 * Secured by network perimeter only; add Spring Security if exposed publicly
 *
 * GET  /kroger/token/status  — returns current token state (secured)
 * POST /kroger/token/refresh — forces an immediate token refresh
 */
@RestController
@RequestMapping("/kroger/token")
public class KrogerTokenController {

    private final KrogerTokenService tokenService;

    public KrogerTokenController(KrogerTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        KrogerTokenState state = tokenService.getCurrentState();
        if (state == null) {
            return ResponseEntity.ok(Map.of("status", "NO_TOKEN", "message", "Token not yet fetched"));
        }
        return ResponseEntity.ok(Map.of(
                "status", state.isExpired() ? "EXPIRED" : "VALID",
                "expiresAt", state.expiresAt().toString(),
                "tokenPresent", true
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        tokenService.forceRefresh();
        KrogerTokenState state = tokenService.getCurrentState();
        return ResponseEntity.ok(Map.of(
                "status", "REFRESHED",
                "expiresAt", state.expiresAt().toString()
        ));
    }
}
