package com.fksoft.erp.domain.crm.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: result of a Lead interaction (managed cadastro). */
@Entity
@Table(name = "interaction_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionResult extends ReferenceData {

    /**
     * Results that do NOT represent an effective contact: a failed attempt that preserves history but
     * must not move a NEW Lead to CONTACTED. Every other result (incl. "needs follow-up" and "other")
     * counts as effective contact, so a result added later via the cadastro is effective by default.
     */
    private static final Set<String> NON_EFFECTIVE_CONTACT_CODES = Set.of("NO_ANSWER", "INVALID_CONTACT");

    /**
     * Creates a new active InteractionResult.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new InteractionResult
     */
    public static InteractionResult create(String code, String label, int sortOrder) {
        InteractionResult result = new InteractionResult();
        result.init(code, label, sortOrder);
        return result;
    }

    /**
     * Whether reaching this result counts as an effective contact (the Lead was actually reached), so
     * that a NEW Lead should move to CONTACTED. False only for the non-effective attempts
     * ("no answer", "invalid contact").
     *
     * @return {@code true} unless this result is a non-effective contact attempt
     */
    public boolean isEffectiveContact() {
        return !NON_EFFECTIVE_CONTACT_CODES.contains(code());
    }
}
