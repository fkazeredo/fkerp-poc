package com.fksoft.erp.domain.workflow;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Catalog of the available workflow steps: every {@link WorkflowGuard} and {@link WorkflowPostFunction}
 * bean in the application, indexed by {@link WorkflowGuard#key()} / {@link WorkflowPostFunction#key()}. The
 * {@link WorkflowEngine} resolves a {@link WorkflowRule}'s {@code ruleKey} against it; the authoring UI
 * lists the available keys. Beans live in their owning domain; this neutral registry just wires them.
 */
@Component
public class WorkflowStepRegistry {

    private final Map<String, WorkflowGuard> guards;
    private final Map<String, WorkflowPostFunction> postFunctions;

    /**
     * @param guards every registered guard bean (may be empty before any domain config is present)
     * @param postFunctions every registered post-function bean
     */
    public WorkflowStepRegistry(List<WorkflowGuard> guards, List<WorkflowPostFunction> postFunctions) {
        this.guards = guards.stream().collect(Collectors.toMap(WorkflowGuard::key, Function.identity()));
        this.postFunctions =
                postFunctions.stream().collect(Collectors.toMap(WorkflowPostFunction::key, Function.identity()));
    }

    /**
     * Resolves a guard by key.
     *
     * @param key the catalog key
     * @return the guard
     * @throws UnknownWorkflowStepException if no guard is registered for the key
     */
    public WorkflowGuard guard(String key) {
        WorkflowGuard guard = guards.get(key);
        if (guard == null) {
            throw new UnknownWorkflowStepException(key);
        }
        return guard;
    }

    /**
     * Resolves a post function by key.
     *
     * @param key the catalog key
     * @return the post function
     * @throws UnknownWorkflowStepException if no post function is registered for the key
     */
    public WorkflowPostFunction postFunction(String key) {
        WorkflowPostFunction postFunction = postFunctions.get(key);
        if (postFunction == null) {
            throw new UnknownWorkflowStepException(key);
        }
        return postFunction;
    }

    /**
     * The available guard keys (sorted), for the authoring catalog.
     *
     * @return the guard keys
     */
    public Set<String> guardKeys() {
        return new TreeSet<>(guards.keySet());
    }

    /**
     * The available post-function keys (sorted), for the authoring catalog.
     *
     * @return the post-function keys
     */
    public Set<String> postFunctionKeys() {
        return new TreeSet<>(postFunctions.keySet());
    }
}
