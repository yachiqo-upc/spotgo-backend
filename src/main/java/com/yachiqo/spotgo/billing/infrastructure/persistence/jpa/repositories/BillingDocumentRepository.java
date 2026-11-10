package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.billing.domain.model.aggregates.BillingDocument;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface BillingDocumentRepository extends JpaRepository<BillingDocument,UUID> {
 @Query("select d from BillingDocument d where d.payment.id=:id") Optional<BillingDocument> byPayment(@Param("id") UUID id);
}
