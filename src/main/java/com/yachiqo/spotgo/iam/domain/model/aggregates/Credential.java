package com.yachiqo.spotgo.iam.domain.model.aggregates;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Check;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "credentials", uniqueConstraints = @UniqueConstraint(
    name = "uk_credential_external_identity", columnNames = {"authentication_method", "external_subject"}))
@Check(constraints = "(authentication_method = 'PASSWORD' and secret_hash is not null and external_subject is null) or (authentication_method = 'GOOGLE' and external_subject is not null and secret_hash is null)")
@Getter
public class Credential {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "credential_id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccount account;
    @Enumerated(EnumType.STRING) @Column(name = "authentication_method", nullable = false)
    private AuthenticationMethod authenticationMethod;
    @Column(name = "secret_hash") private String secretHash;
    @Column(name = "external_subject") private String externalSubject;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false)
    private CredentialStatus status;
    protected Credential() {}
    private Credential(UserAccount account, AuthenticationMethod method) {
        this.account = Objects.requireNonNull(account); this.authenticationMethod = method; this.status = CredentialStatus.ACTIVE;
    }
    public static Credential password(UserAccount account, String hash) {
        var credential = new Credential(account, AuthenticationMethod.PASSWORD);
        credential.rotate(hash); return credential;
    }
    public static Credential google(UserAccount account, String subject) {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Google requiere external_subject verificado");
        var credential = new Credential(account, AuthenticationMethod.GOOGLE);
        credential.externalSubject = subject; return credential;
    }
    public UUID getAccountId() { return account.getId(); }
    public void usedAt(Instant now) { this.lastUsedAt = now; }
    public void rotate(String hash) {
        if (authenticationMethod != AuthenticationMethod.PASSWORD || hash == null || hash.isBlank())
            throw new IllegalArgumentException("Solo una credencial PASSWORD puede recibir un hash");
        this.secretHash = hash;
    }
    public void revoke() { status = CredentialStatus.REVOKED; }
}
