package com.fksoft.erp.domain.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

/** Repository for {@link WorkflowTransition}s. */
public interface WorkflowTransitionRepository extends ListCrudRepository<WorkflowTransition, UUID> {

    Optional<WorkflowTransition> findByDefinition_CodeAndFromState_CodeAndCode(
            String definitionCode, String fromStateCode, String code);

    List<WorkflowTransition> findByDefinition_CodeOrderBySortOrderAsc(String definitionCode);

    List<WorkflowTransition> findByDefinition_CodeAndFromState_CodeOrderBySortOrderAsc(
            String definitionCode, String fromStateCode);
}
