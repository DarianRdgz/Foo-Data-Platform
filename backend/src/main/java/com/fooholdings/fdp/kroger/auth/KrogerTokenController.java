package com.fooholdings.fdp.kroger.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/kroger/token")
public class KrogerTokenController {

    private final KrogerTokenService tokenService;

    public KrogerTokenController(KrogerTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/status")
    public KrogerTokenService.TokenStatus status() {
        return tokenService.getStatus();
    }

}
