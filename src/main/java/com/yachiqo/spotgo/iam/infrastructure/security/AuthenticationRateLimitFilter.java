package com.yachiqo.spotgo.iam.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_SECONDS = 60;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AuthenticationRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        var path = request.getRequestURI();
        return !path.endsWith("/sign-in")
                && !path.endsWith("/sign-up")
                && !path.endsWith("/password-reset/request")
                && !path.endsWith("/password-reset/confirm");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var now = Instant.now(clock);
        var key = request.getRequestURI() + "|" + request.getRemoteAddr();
        var retryAfter = registerAndGetRetryAfter(key, now);
        if (retryAfter > 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"code":"RATE_LIMITED","message":"Too many authentication attempts. Please try again later."}""");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private long registerAndGetRetryAfter(String key, Instant now) {
        var window = windows.compute(key, (ignored, current) -> {
            if (current == null || now.isAfter(current.expiresAt())) {
                return new Window(1, now.plusSeconds(WINDOW_SECONDS));
            }
            return new Window(current.count() + 1, current.expiresAt());
        });
        if (window.count() <= MAX_REQUESTS) {
            return 0;
        }
        return Math.max(1, window.expiresAt().getEpochSecond() - now.getEpochSecond());
    }

    private record Window(int count, Instant expiresAt) {
    }
}
