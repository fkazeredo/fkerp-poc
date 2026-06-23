package com.fksoft.erp.domain.workflow;

import com.fksoft.erp.domain.workflow.WorkflowAttentionConditionCatalog.ConditionDescriptor;
import java.util.List;
import java.util.Map;

/**
 * The authoring catalog the workflow editor lists: the available attention-condition descriptors per
 * workflow definition, plus the registered transition guard / post-function keys.
 *
 * @param attentionConditions the attention-condition descriptors keyed by workflow definition code
 * @param guardKeys the registered transition guard catalog keys
 * @param postFunctionKeys the registered transition post-function catalog keys
 */
public record WorkflowCatalogView(
        Map<String, List<ConditionDescriptor>> attentionConditions,
        List<String> guardKeys,
        List<String> postFunctionKeys) {}
