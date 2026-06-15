package com.fksoft.erp.infra.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable, framework-free pagination envelope shared by list APIs.
 *
 * @param content the page items
 * @param page zero-based page number
 * @param size page size
 * @param totalElements total matching elements
 * @param totalPages total number of pages
 * @param first whether this is the first page
 * @param last whether this is the last page
 * @param <T> item type
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {

    /**
     * Maps a Spring Data {@link Page} into a transport envelope.
     *
     * @param page the source page
     * @param mapper maps each source element to its DTO
     * @param <E> source element type
     * @param <T> target DTO type
     * @return the envelope
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
