package com.fksoft.erp.domain.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

/** Repository for {@link WorkflowState}s. */
public interface WorkflowStateRepository extends ListCrudRepository<WorkflowState, UUID> {

    Optional<WorkflowState> findByDefinition_CodeAndCode(String definitionCode, String code);

    List<WorkflowState> findByDefinition_CodeOrderBySortOrderAsc(String definitionCode);
}
