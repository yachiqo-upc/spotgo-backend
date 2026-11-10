package com.yachiqo.spotgo.iam.infrastructure.security;
import com.yachiqo.spotgo.iam.application.SessionAuthenticationService;
import com.yachiqo.spotgo.iam.domain.model.aggregates.UserAccount;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.UserAccountRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SpotgoUserDetailsService implements UserDetailsService {
    private final UserAccountRepository accounts;
    private final SessionAuthenticationService authentication;
    public SpotgoUserDetailsService(UserAccountRepository accounts, SessionAuthenticationService authentication) {
        this.accounts = accounts; this.authentication = authentication;
    }
    @Override public UserDetails loadUserByUsername(String email) {
        var account = accounts.findByEmail(UserAccount.normalizeEmail(email)).filter(UserAccount::isActive)
            .orElseThrow(() -> new UsernameNotFoundException("Cuenta no disponible"));
        return authentication.principal(account, null);
    }
}
