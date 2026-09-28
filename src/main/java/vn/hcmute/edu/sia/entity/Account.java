package vn.hcmute.edu.sia.entity;
import vn.hcmute.edu.sia.enums.*;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
@Inheritance (strategy = InheritanceType.JOINED)
public abstract class Account {
    @Id 
    @GeneratedValue ()
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID id;
    
    @Column (name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column (name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column (name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated (EnumType.STRING)
    @Column (name = "role", nullable = false, length = 20, updatable = false)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountAccessStatus accessStatus;

    @Column(name = "must_change_password", nullable = false)
    private boolean passwordChangeRequired;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updateAt;

    //Constructor
    protected Account() {
        // Constructor required by JPA.
    }

    protected Account(
            String fullName,
            String email,
            String passwordHash,
            AccountRole role,
            AccountAccessStatus accessStatus,
            boolean passwordChangeRequired
    ) {
        this.fullName = normalizeFullName(fullName);
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.accessStatus = accessStatus;
        this.passwordChangeRequired = passwordChangeRequired;
    }
    private String normalizeFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank");
        }

        return fullName.trim();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }

        return email.trim().toLowerCase();
    }

    public AccountAccessStatus getAccessStatus() {
        return accessStatus;
    }

    public String getPasswordHash(){
        return passwordHash;
    }

    public AccountRole getRole() {
        return role;
    }
    
    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public UUID getId(){
        return id;
    }

    public String getEmail()
    {
        return email;
    }
}
