package com.fksoft.erp.domain.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

/** Repository for {@link WorkflowDefinition}s. */
public interface WorkflowDefinitionRepository extends ListCrudRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByCode(String code);

    List<WorkflowDefinition> findAllByOrderByCodeAsc();
}
