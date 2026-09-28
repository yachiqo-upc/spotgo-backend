package com.yachiqo.spotgo.iam.infrastructure.security;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class SpotgoUserPrincipal implements UserDetails {

    private final String userId;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final Long tokenVersion;
    private final List<GrantedAuthority> authorities;

    public SpotgoUserPrincipal(String userId, String email, String passwordHash, UserRole role, Long tokenVersion) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.tokenVersion = tokenVersion;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public String getUserId() {
        return userId;
    }

    public UserRole getRole() {
        return role;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
