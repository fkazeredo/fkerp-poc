package com.fksoft.erp.application.api.dto;

import java.util.UUID;

/** A user that can be assigned as a Lead responsible (id + display name). */
public record ResponsibleResponse(UUID id, String name) {}
