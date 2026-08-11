package com.askvocate.backend.model;

import com.askvocate.backend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for CLIENT role users (collection: "clients").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("clients")
public class ClientProfile {

    @Id
    private String id;

    /** Always Role.CLIENT */
    @Builder.Default
    private Role role = Role.CLIENT;

    private String name;

    @Indexed(unique = true)
    private String emailOrPhone;

    private String passwordHash;

    @Builder.Default
    private Long createdAt = Instant.now().toEpochMilli();
}
