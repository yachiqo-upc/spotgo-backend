package com.yachiqo.spotgo.iam.infrastructure.security;

import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SpotgoUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public SpotgoUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var normalizedEmail = email == null ? null : email.trim().toLowerCase();
        var user = userAccountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new SpotgoUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.getTokenVersion());
    }
}
