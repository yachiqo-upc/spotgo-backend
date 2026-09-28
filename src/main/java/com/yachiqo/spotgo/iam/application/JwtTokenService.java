package com.yachiqo.spotgo.iam.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final String secret;
    private final long expirationDays;
    private final Clock clock;

    public JwtTokenService(
            @Value("${authorization.jwt.secret}") String secret,
            @Value("${authorization.jwt.expiration.days}") long expirationDays,
            Clock clock) {
        this.secret = secret;
        this.expirationDays = expirationDays;
        this.clock = clock;
    }

    public String generateToken(UserAccount user) {
        var now = Instant.now(clock);
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name().toLowerCase())
                .claim("ver", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationDays, ChronoUnit.DAYS)))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Long extractTokenVersion(String token) {
        return parseClaims(token).get("ver", Long.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey getSigningKey() {
        var bytes = secret.length() >= 32 ? secret.getBytes(StandardCharsets.UTF_8) : Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(bytes);
    }
}
