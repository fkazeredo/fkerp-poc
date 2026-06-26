package com.fksoft.erp.domain.crm.model;

/**
 * The Customer lifecycle (Customer Management), a fixed set of states. A Customer profile starts {@code ACTIVE}
 * when consolidated from a closed deal; {@code INACTIVE} marks a client the company no longer transacts with and
 * {@code BLOCKED} an internally restricted client. The status transitions (activate / deactivate / block) are a
 * later slice — Sprint 7 Slice 1 only creates Active. Persisted as its name ({@code @Enumerated(STRING)}, mirrored
 * by a DB {@code CHECK}); the name is the value exposed in the JSON contract.
 */
public enum CustomerStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED;

    /**
     * Whether the customer is currently active (transacting).
     *
     * @return {@code true} only for {@code ACTIVE}
     */
    public boolean isActive() {
        return this == ACTIVE;
    }
}
