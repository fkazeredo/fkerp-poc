package com.fksoft.erp.domain.crm;

/** Lead count grouped by responsible person ({@code responsibleName == null} means unassigned). */
public record ResponsibleCountView(String responsibleName, long count) {}
