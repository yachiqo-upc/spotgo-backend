package com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.converters;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole role) {
        return role == null ? null : role.name().toLowerCase();
    }

    @Override
    public UserRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserRole.valueOf(dbData.trim().toUpperCase());
    }
}
