package com.yachiqo.spotgo.iam.domain.model.aggregates;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Check;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "role_assignments")
@Check(constraints = "(valid_to is null or valid_to >= valid_from) and ((role_type = 'PARKING_ADMIN' and tenant_id is not null) or (role_type <> 'PARKING_ADMIN' and tenant_id is null))")
@Getter
public class RoleAssignment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id", nullable = false, updatable = false)
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true, updatable = false)
    private Account account;
    @Enumerated(EnumType.STRING) @Column(name = "role_type", nullable = false)
    private UserRole roleType;
    // UUID scalar for the API; read-only association below enforces the tenant FK.
    @Column(name = "tenant_id") private UUID tenantId;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="tenant_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_role_tenant"))
    private com.yachiqo.spotgo.parking.domain.model.aggregates.Tenant tenant;
    @Column(name = "valid_from", nullable = false) private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false)
    private AssignmentStatus status;
    protected RoleAssignment() {}
    public RoleAssignment(Account account, UserRole role, UUID tenant, Instant from, Instant to, AssignmentStatus status) {
        this.account = Objects.requireNonNull(account); assign(role, tenant, from, to, status);
    }
    public void assign(UserRole role, UUID tenant, Instant from, Instant to, AssignmentStatus status) {
        Objects.requireNonNull(role); Objects.requireNonNull(from); Objects.requireNonNull(status);
        if (to != null && to.isBefore(from)) throw new IllegalArgumentException("valid_to no puede ser anterior a valid_from");
        if ((role == UserRole.PARKING_ADMIN) != (tenant != null))
            throw new IllegalArgumentException("PARKING_ADMIN requiere tenantId; DRIVER y SUPER_ADMIN tienen alcance global sin tenantId");
        this.roleType = role; this.tenantId = tenant; this.validFrom = from; this.validTo = to; this.status = status;
    }
    public UUID getAccountId() { return account.getId(); }
    public boolean isEffectiveAt(Instant now) {
        return status == AssignmentStatus.ACTIVE && !now.isBefore(validFrom) && (validTo == null || now.isBefore(validTo));
    }
}
