package com.fksoft.erp.domain.crm;

import java.util.UUID;

/** Raw query carrier: Lead count grouped by responsible id ({@code null} = unassigned). */
public record ResponsibleCount(UUID responsibleId, long count) {}
