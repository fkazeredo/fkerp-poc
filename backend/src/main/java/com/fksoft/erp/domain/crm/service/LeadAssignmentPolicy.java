package com.fksoft.erp.domain.crm.service;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Business authorization for assigning a Lead's responsible person (§10). Full assignment authority
 * (scope {@code crm:lead:assign}, held by commercial managers and administrators) may set any active
 * user as responsible, or unassign. Without it, a user may only set <b>themselves</b> as responsible
 * (self-claim) — never another user, and never unassign.
 */
@Component
public class LeadAssignmentPolicy {

    /**
     * Tells whether the actor may set the given responsible person.
     *
     * @param actorId the user performing the change
     * @param toResponsibleId the requested new responsible (or {@code null} to unassign)
     * @param hasAssignScope whether the actor holds full assignment authority
     * @return {@code true} if the change is allowed
     */
    public boolean canAssign(UUID actorId, UUID toResponsibleId, boolean hasAssignScope) {
        if (hasAssignScope) {
            return true;
        }
        return toResponsibleId != null && Objects.equals(toResponsibleId, actorId);
    }
}
