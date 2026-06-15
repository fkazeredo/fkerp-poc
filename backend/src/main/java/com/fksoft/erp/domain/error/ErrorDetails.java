package com.fksoft.erp.domain.error;

import java.util.Map;

/**
 * Optional extra domain data carried by a {@link DomainException}. Key/value pairs are mapped to
 * the {@code fields} of the API error response.
 */
public interface ErrorDetails {

    Map<String, String> details();
}
