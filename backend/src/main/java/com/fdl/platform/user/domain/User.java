package com.fdl.platform.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Operador autenticado. O e-mail é guardado normalizado (sem espaços, em minúsculas). */
@Entity
@Table(name = "users")
public class User {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public static User create(String name, String email, String passwordHash, Clock clock) {
        var user = new User();
        var now = clock.instant();
        user.id = UUID.randomUUID();
        user.name = name.strip();
        user.email = normalizeEmail(email);
        user.passwordHash = passwordHash;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
