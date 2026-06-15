package com.fksoft.erp.domain.crm;

import java.time.Instant;

/** Read view of a Lead's qualification outcome. */
public record QualificationView(Instant qualifiedAt, String qualifiedByName, String note) {}
