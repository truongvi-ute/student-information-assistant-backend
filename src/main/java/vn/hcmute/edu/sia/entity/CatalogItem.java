package vn.hcmute.edu.sia.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import vn.hcmute.edu.sia.enums.CatalogStatus;

@MappedSuperclass
public abstract class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected CatalogItem() {
    }

    protected CatalogItem(String name) {
        this.name = normalizeName(name);
        this.status = CatalogStatus.ACTIVE;
    }

    public void rename(String newName) {
        this.name = normalizeName(newName);
        this.updatedAt = OffsetDateTime.now();
    }

    public void activate() {
        this.status = CatalogStatus.ACTIVE;
        this.updatedAt = OffsetDateTime.now();
    }

    public void deactivate() {
        this.status = CatalogStatus.INACTIVE;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return this.status == CatalogStatus.ACTIVE;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Catalog item name must not be blank");
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CatalogStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
