package com.fksoft.erp.domain.identity;

import java.util.UUID;

/** Read view of a user assignable as a Lead responsible (id + display name). */
public record ResponsibleView(UUID id, String name) {

    public static ResponsibleView from(User user) {
        return new ResponsibleView(user.id(), user.username());
    }
}
