package com.krx2.employeedatamanagement.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final List<String> EXCLUDED_PATH_PREFIXES = List.of("/swagger-ui", "/v3/api-docs");

    private final byte[] expectedApiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(@Value("${app.api-key}") String expectedApiKey, ObjectMapper objectMapper) {
        this.expectedApiKey = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String providedApiKey = request.getHeader(API_KEY_HEADER);
        byte[] provided = providedApiKey == null
                ? new byte[0]
                : providedApiKey.getBytes(StandardCharsets.UTF_8);

        // MessageDigest.isEqual runs in time independent of *where* the arrays differ,
        // unlike String.equals, which can leak a secret's length/prefix through timing.
        if (!MessageDigest.isEqual(expectedApiKey, provided)) {
            ProblemDetail problemDetail =
                    ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Missing or invalid API key");
            problemDetail.setTitle("Unauthorized");
            problemDetail.setInstance(URI.create(request.getRequestURI()));

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            objectMapper.writeValue(response.getOutputStream(), problemDetail);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
