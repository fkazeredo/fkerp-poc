package com.fksoft.erp.domain.crm;

/** Lead count grouped by origin (for the indicators view). */
public record OriginCountView(String origin, long count) {}
