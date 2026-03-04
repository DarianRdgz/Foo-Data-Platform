package com.fooholdings.fdp.admin.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminApiKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-API-KEY";
    private final AdminSecurityProperties props;

    public AdminApiKeyInterceptor(AdminSecurityProperties props) {
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        String expected = props.apiKey();
        if (expected == null || expected.isBlank()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Admin API key not configured.");
            return false;
        }

        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(expected, provided)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid API key.");
            return false;
        }

        return true;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }
}