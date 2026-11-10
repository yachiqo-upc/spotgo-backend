package com.yachiqo.spotgo.profiles.domain.model.aggregates;

import com.yachiqo.spotgo.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

// Captures the current timestamp when the entity is created or updated.
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import static com.yachiqo.spotgo.profiles.domain.model.valueobjects.ProfileText.required;

@Getter // Lombok generates the PK accessor getId() and FK accessor getAccountId().
@Entity //JPA reconoce esto como una entidad de guardado
@Table(name = "profiles")

// Profile entity structure.
public class Profile extends AbstractDomainAggregateRoot<Profile>{
    
    //Atributos
    //Profile id
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "profile_id", nullable = false, updatable = false)
    private UUID id;

    //Account id
    @Column(name = "account_id", nullable = false, unique = true, updatable = false)
    private UUID accountId;

    // Enforces the physical FK while keeping the UUID-based constructor and API.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_profile_account"))
    private com.yachiqo.spotgo.iam.domain.model.aggregates.Account account;

    //First name
    @Column(name = "first_name", nullable = false)
    private String firstName;

    //Last name
    @Column(name = "last_name", nullable = false)
    private String lastName;

    //Phone
    @Column(name = "phone", nullable = false)
    private String phone;

    //Created at
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    //Updated at
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    //Constructor
    protected Profile() {
        // JPA uses this constructor when loading profiles from the database.
    }

    
    public Profile(
            UUID accountId,
            String firstName,
            String lastName,
            String phone
    ) {
        this.accountId = Objects.requireNonNull(
                accountId, "Account is required."
        );
        update(firstName, lastName, phone);
    }


    public void update(String firstName, String lastName, String phone) {
        this.firstName = required(firstName, "Nombres");
        this.lastName = required(lastName, "Apellidos");
        this.phone = required(phone, "Telefono");
    }

    // Domain methods.

    // Set both timestamps when the profile is created.
    @PrePersist //Insert
    protected void onCreate(){
        Instant now= Instant.now();

        // Use the same instant for both fields.
        this.createdAt = now;
        this.updatedAt=now;
    }

    // Update the modification timestamp when the profile changes.
    @PreUpdate //Update
    protected void onUpdate(){
        this.updatedAt = Instant.now();
    }


}
