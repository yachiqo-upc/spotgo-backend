package com.yachiqo.spotgo.iam.infrastructure.security;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class SpotgoUserPrincipal implements UserDetails {
    private final UUID accountId;
    private final String email;
    private final UserRole role;
    private final UUID tenantId;
    private final UUID sessionId;
    public SpotgoUserPrincipal(UUID accountId, String email, UserRole role, UUID tenantId, UUID sessionId) {
        this.accountId = Objects.requireNonNull(accountId); this.email = email; this.role = role;
        this.tenantId = tenantId; this.sessionId = sessionId;
    }
    public UUID getAccountId() { return accountId; }
    public UserRole getRole() { return role; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSessionId() { return sessionId; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return ""; }
    @Override public String getUsername() { return email; }
}
