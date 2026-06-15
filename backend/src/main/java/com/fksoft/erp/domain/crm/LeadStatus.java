package com.fksoft.erp.domain.crm;

/** Lead lifecycle (Sprint 1). Creation always starts at {@link #NEW}; transitions arrive in later slices. */
public enum LeadStatus {
    NEW,
    CONTACTED,
    QUALIFIED,
    LOST
}
