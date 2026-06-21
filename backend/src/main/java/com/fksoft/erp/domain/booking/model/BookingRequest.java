package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.booking.exception.BookingItemNotMarkableException;
import com.fksoft.erp.domain.booking.exception.CommercialOrderNotPendingBookingException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderItem;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A Booking Request: the operational record that starts the (still manual) reservation process for a
 * Commercial Order that is {@link CommercialOrderStatus#PENDING_BOOKING}. Aggregate root of the Booking
 * Operations context ({@code domain.booking}). It preserves the source Commercial Order / Proposal /
 * Opportunity / Lead references and the commercial responsible, and snapshots <b>what</b> must be reserved
 * (the booking items, classified by booking need) — carrying <b>no monetary data</b>. A Booking Request is
 * NOT an external integration, a Receivable, a Payment, a Commission or Customer Care. It starts
 * {@link BookingRequestStatus#PENDING}; the operational transitions (attempts, confirmation, failure) are
 * later slices.
 */
@Entity
@Table(name = "booking_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingRequest {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    // The source Commercial Order this request fulfills (preserved; the Order is not modified here).
    @NotNull
    @Column(name = "commercial_order_id", nullable = false, updatable = false)
    private UUID commercialOrderId;

    // Source references, denormalized from the Order for traceability.
    @NotNull
    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    @NotNull
    @Column(name = "opportunity_id", nullable = false, updatable = false)
    private UUID opportunityId;

    @NotNull
    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    // The commercial responsible, preserved from the Order (may be null).
    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    // The assigned booking operator (optional initially; assignment is a later slice).
    @Column(name = "booking_operator_id")
    private UUID bookingOperatorId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingRequestStatus status;

    @Size(max = 2000)
    private String notes;

    // The lines to reserve — a snapshot of the Order's items, classified by booking need.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "booking_request_id", nullable = false)
    private List<BookingItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    /**
     * Creates a Booking Request from a Commercial Order that is PENDING_BOOKING, snapshotting its items
     * (classified by booking need) and preserving the source references and the commercial responsible. The
     * request starts {@link BookingRequestStatus#PENDING}. Creates no external reservation and no Receivable,
     * Payment or Commission data.
     *
     * @param order the source Commercial Order; must be {@link CommercialOrderStatus#PENDING_BOOKING}
     * @param bookingOperatorId the assigned booking operator, or {@code null} (optional initially)
     * @param notes optional booking notes
     * @param bookingRequiredItemIds ids of the Order's OTHER items to mark as requiring booking (each must be
     *     an OTHER item of the Order); {@code null}/empty marks none
     * @param createdBy id of the user creating the request
     * @return a new, unsaved Booking Request
     * @throws CommercialOrderNotPendingBookingException if the Order is not PENDING_BOOKING
     * @throws BookingItemNotMarkableException if a marked id is not an OTHER item of the Order
     */
    public static BookingRequest createFromOrder(
            CommercialOrder order,
            UUID bookingOperatorId,
            String notes,
            Set<UUID> bookingRequiredItemIds,
            UUID createdBy) {
        if (order.status() != CommercialOrderStatus.PENDING_BOOKING) {
            throw new CommercialOrderNotPendingBookingException();
        }
        Set<UUID> required = bookingRequiredItemIds == null ? Set.of() : bookingRequiredItemIds;
        // Only OTHER items of the Order may be explicitly marked as requiring booking (a travel package / car
        // rental already require it; a service fee never does). Reject any other marked id (incl. unknown ones).
        Map<UUID, ProposalItemType> typeById =
                order.items().stream().collect(Collectors.toMap(CommercialOrderItem::id, CommercialOrderItem::type));
        for (UUID id : required) {
            if (typeById.get(id) != ProposalItemType.OTHER) {
                throw new BookingItemNotMarkableException(id);
            }
        }
        BookingRequest request = new BookingRequest();
        request.id = UUID.randomUUID();
        request.commercialOrderId = order.id();
        request.proposalId = order.proposalId();
        request.opportunityId = order.opportunityId();
        request.leadId = order.leadId();
        request.responsiblePersonId = order.responsiblePersonId();
        request.bookingOperatorId = bookingOperatorId;
        request.notes = notes;
        request.status = BookingRequestStatus.PENDING;
        order.items().forEach(item -> request.items.add(BookingItem.snapshotOf(item, required.contains(item.id()))));
        request.createdBy = createdBy;
        request.updatedBy = createdBy;
        return request;
    }
}
