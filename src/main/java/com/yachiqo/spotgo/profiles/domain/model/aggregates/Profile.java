package com.yachiqo.spotgo.profiles.domain.model.aggregates;

import com.yachiqo.spotgo.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

//Me ayuda a capturar el Timestamp en el preciso instante
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import static com.yachiqo.spotgo.profiles.domain.model.valueobjects.ProfileText.required;

@Getter //Lombok me crea metodos PK getId() y la FK getAccountId()
@Entity //JPA reconoce esto como una entidad de guardado
@Table(name = "profiles")

//Estructura perfil
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
        // JPA lo utiliza al leer perfiles de la BD.
    }

    
    public Profile(
            UUID accountId,
            String firstName,
            String lastName,
            String phone
    ) {
        this.accountId = Objects.requireNonNull(
                accountId, "La cuenta es obligatoria"
        );
        update(firstName, lastName, phone);
    }


    public void update(String firstName, String lastName, String phone) {
        this.firstName = required(firstName, "Nombres");
        this.lastName = required(lastName, "Apellidos");
        this.phone = required(phone, "Telefono");
    }

    //Metodos

    //Creacion de perfil, cuando se crea coloca las fechas en created y updated
    @PrePersist //Insert
    protected void onCreate(){
        Instant now= Instant.now();

        //A los dos
        this.createdAt = now;
        this.updatedAt=now;
    }

    //Actualizacion de perfil, cuando se actualiza, solo se modifica el update at.
    @PreUpdate //Update
    protected void onUpdate(){
        this.updatedAt = Instant.now();
    }


}
