package com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.iam.domain.model.aggregates.Credential;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CredentialRepository extends JpaRepository<Credential, UUID> {
    @Query("select c from Credential c where c.account.id = :accountId and c.authenticationMethod = :method and c.status = :status order by c.id")
    List<Credential> findAllByAccountIdAndAuthenticationMethodAndStatus(@Param("accountId") UUID accountId,
        @Param("method") AuthenticationMethod method, @Param("status") CredentialStatus status);
    @Query("select c from Credential c where c.authenticationMethod = com.yachiqo.spotgo.iam.domain.model.valueobjects.AuthenticationMethod.GOOGLE and c.externalSubject = :subject")
    Optional<Credential> findGoogleByExternalSubject(@Param("subject") String subject);
    @Query("select c from Credential c where c.account.id = :accountId")
    List<Credential> findAllByAccountId(@Param("accountId") UUID accountId);
}
