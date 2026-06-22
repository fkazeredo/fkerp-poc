package com.fksoft.erp.domain.reference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base repository for {@link ReferenceData} cadastros (any domain).
 *
 * @param <T> the reference-data type
 */
@NoRepositoryBean
public interface ReferenceDataRepository<T extends ReferenceData> extends ListCrudRepository<T, UUID> {

    Optional<T> findByCode(String code);

    List<T> findAllByOrderBySortOrderAsc();

    List<T> findByActiveTrueOrderBySortOrderAsc();
}
