package com.fksoft.erp.domain.sales.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Reference data: the type of a commercial-offer line (a {@link ProposalItem}, a {@link CommercialOrderItem}
 * or a {@code BookingItem}). A managed cadastro carrying an extra {@code requiresBooking} attribute that
 * classifies whether an item of this type needs a booking operation (it absorbs the former booking-need
 * classification). The codes {@code TRAVEL_PACKAGE} and {@code CAR_RENTAL} are reserved: they anchor the
 * type-specific confirmation flows. The anchoring stays safe because the {@code code} is immutable and a
 * value is only ever soft-deleted (deactivated), never removed — so an existing item keeps resolving its
 * reserved type even if an admin renames or deactivates it.
 */
@Entity
@Table(name = "proposal_item_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalItemType extends ReferenceData {

    @Column(name = "requires_booking", nullable = false)
    private boolean requiresBooking;

    /**
     * Creates a new active ProposalItemType.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @param requiresBooking whether items of this type require a booking operation
     * @return the new ProposalItemType
     */
    public static ProposalItemType create(String code, String label, int sortOrder, boolean requiresBooking) {
        ProposalItemType type = new ProposalItemType();
        type.init(code, label, sortOrder);
        type.requiresBooking = requiresBooking;
        return type;
    }

    /**
     * Updates whether items of this type require a booking operation.
     *
     * @param newRequiresBooking the new booking-requirement flag
     */
    public void changeRequiresBooking(boolean newRequiresBooking) {
        this.requiresBooking = newRequiresBooking;
    }
}
