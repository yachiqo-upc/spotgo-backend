package com.yachiqo.spotgo.iam.domain.model.aggregates;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.converters.UserRoleConverter;
import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class UserAccount extends UuidIdentifiedAggregateRoot<UserAccount> {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String phone;

    @Convert(converter = UserRoleConverter.class)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private Long tokenVersion = 0L;

    protected UserAccount() {
    }

    public UserAccount(String firstName, String lastName, String email, String passwordHash, String phone, UserRole role) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.phone = phone == null ? "" : phone;
        this.role = role;
    }

    public void updateProfile(String firstName, String lastName, String phone) {
        if (firstName != null) this.firstName = firstName;
        if (lastName != null) this.lastName = lastName;
        if (phone != null) this.phone = phone;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.tokenVersion = this.tokenVersion + 1;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
