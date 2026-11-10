package com.yachiqo.spotgo.iam.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final SecretKey key;
    private final long expirationMinutes;
    private final Clock clock;
    public JwtTokenService(@Value("${authorization.jwt.secret}") String secret,
                           @Value("${authorization.jwt.access-expiration-minutes:15}") long expirationMinutes, Clock clock) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("JWT_SECRET debe tener al menos 32 bytes");
        if (expirationMinutes <= 0) throw new IllegalArgumentException("La duracion del access token debe ser positiva");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes; this.clock = clock;
    }
    public String generateToken(Account account, UserRole role, Session session) {
        Instant now = clock.instant();
        return Jwts.builder().subject(account.getId().toString())
            .claim("sid", session.getId().toString()).claim("role", role == null ? null : role.name())
            .claim("type", "ACCESS").issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt())).signWith(key).compact();
    }
    public Instant expiresAt() { return clock.instant().plusSeconds(expirationMinutes * 60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS); }
    public AccessClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser().clock(() -> Date.from(clock.instant())).verifyWith(key)
            .build().parseSignedClaims(token).getPayload();
        if (!"ACCESS".equals(claims.get("type", String.class))) throw new IllegalArgumentException("Tipo de token invalido");
        if (claims.getSubject() == null || claims.get("sid", String.class) == null)
            throw new IllegalArgumentException("El token requiere cuenta y sesion");
        return new AccessClaims(UUID.fromString(claims.getSubject()), UUID.fromString(claims.get("sid", String.class)), claims.getExpiration().toInstant());
    }
    public record AccessClaims(UUID accountId, UUID sessionId, Instant expiresAt) {}
}
