package com.fksoft.erp.domain.booking.service;

import com.fksoft.erp.domain.booking.model.BookingRequest;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Business authorization for reading Booking Requests (§10), mirroring the other contexts' read model as three
 * escalating visibility tiers driven by scopes:
 *
 * <ul>
 *   <li><b>own only</b> ({@code booking:request:read}) — only requests the user owns (the assigned booking
 *       operator) or is the commercial responsible for;
 *   <li><b>own + unassigned pool</b> (also {@code booking:request:read:unassigned}) — plus requests with no
 *       booking operator yet;
 *   <li><b>all</b> ({@code booking:request:read:all}) — every request (Operations leads, Managers, Board).
 * </ul>
 *
 * <p>Only the read-all tier is seeded today; the own/unassigned tiers are defined here so future operator
 * profiles plug in without touching the query.
 */
@Component
public class BookingRequestAccessPolicy {

    /**
     * Builds the visibility predicate for the Booking Request list for the given user.
     *
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @param canSeeUnassigned whether the user may also see the unassigned (no-operator) pool
     * @return a Specification restricting visible requests (always-true when {@code canSeeAll})
     */
    public Specification<BookingRequest> visibleTo(UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        if (canSeeAll) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            var own = cb.or(
                    cb.equal(root.get("bookingOperatorId"), userId), cb.equal(root.get("responsiblePersonId"), userId));
            return canSeeUnassigned ? cb.or(own, cb.isNull(root.get("bookingOperatorId"))) : own;
        };
    }

    /**
     * Tells whether a user may see a single Booking Request: they hold read-all (sees all), are its booking
     * operator or commercial responsible, or it has no operator yet and they may see the pool.
     *
     * @param request the booking request
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @param canSeeUnassigned whether the user may also see the unassigned (no-operator) pool
     * @return {@code true} if the request is visible to the user
     */
    public boolean canSee(BookingRequest request, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        if (canSeeAll) {
            return true;
        }
        if (userId != null
                && (userId.equals(request.bookingOperatorId()) || userId.equals(request.responsiblePersonId()))) {
            return true;
        }
        return request.bookingOperatorId() == null && canSeeUnassigned;
    }
}
