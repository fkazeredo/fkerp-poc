package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.identity.ResponsibleView;
import java.util.UUID;

/** A user that can be assigned as a Lead responsible. */
public record ResponsibleResponse(UUID id, String name) {

    public static ResponsibleResponse from(ResponsibleView view) {
        return new ResponsibleResponse(view.id(), view.name());
    }
}
