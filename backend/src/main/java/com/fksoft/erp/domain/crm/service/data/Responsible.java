package com.fksoft.erp.domain.crm.service.data;

import java.util.UUID;

/** A user that can be assigned as a Lead responsible (id + display name). */
public record Responsible(UUID id, String name) {}
