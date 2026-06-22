package com.fksoft.erp.domain.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Neutral base for reference-data cadastros across every domain (origins, loss reasons, interaction
 * types/results, sending channels, booking reasons, …). Each value has a stable {@code code}, a display
 * {@code label}, an {@code active} flag (soft delete) and a sort order. Inactive values cannot be used by
 * new records but remain for historical integrity. This kernel is context-neutral: concrete cadastros live
 * in their owning domain (CRM/Sales/Booking) and extend this type.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ReferenceData {

    @Id
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean active;

    @PositiveOrZero
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Initializes a new active reference value.
     *
     * @param newCode stable code
     * @param newLabel display label
     * @param newSortOrder sort order
     */
    protected void init(String newCode, String newLabel, int newSortOrder) {
        this.id = UUID.randomUUID();
        this.code = newCode;
        this.label = newLabel;
        this.active = true;
        this.sortOrder = newSortOrder;
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    public void reorder(int newSortOrder) {
        this.sortOrder = newSortOrder;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
