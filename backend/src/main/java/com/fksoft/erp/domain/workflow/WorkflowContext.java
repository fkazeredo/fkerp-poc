package com.fksoft.erp.domain.workflow;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The execution context passed to a transition's {@link WorkflowGuard}s and {@link WorkflowPostFunction}s.
 * It carries the subject being transitioned (the aggregate, e.g. a {@code Lead} or {@code Opportunity}),
 * the acting user and scopes, the request payload ({@code params}), and — once the engine resolves it —
 * the {@link WorkflowTransition} (and therefore the from/to states). Concrete rule beans read the subject
 * via {@link #subjectAs(Class)} and the payload via {@link #param(String)}.
 */
public final class WorkflowContext {

    private final Object subject;
    private final UUID actorUserId;
    private final Set<String> actorScopes;
    private final Map<String, Object> params;
    private WorkflowTransition transition;

    private WorkflowContext(Object subject, UUID actorUserId, Set<String> actorScopes, Map<String, Object> params) {
        this.subject = subject;
        this.actorUserId = actorUserId;
        this.actorScopes = actorScopes == null ? Set.of() : Set.copyOf(actorScopes);
        this.params = params == null ? Map.of() : Map.copyOf(params);
    }

    /**
     * Creates a context for a transition.
     *
     * @param subject the aggregate being transitioned
     * @param actorUserId the acting user id
     * @param actorScopes the acting user's scopes (may be empty)
     * @param params the request payload (may be empty)
     * @return the context
     */
    public static WorkflowContext of(
            Object subject, UUID actorUserId, Set<String> actorScopes, Map<String, Object> params) {
        return new WorkflowContext(subject, actorUserId, actorScopes, params);
    }

    /**
     * Creates a context with no scopes and no payload.
     *
     * @param subject the aggregate being transitioned
     * @param actorUserId the acting user id
     * @return the context
     */
    public static WorkflowContext of(Object subject, UUID actorUserId) {
        return new WorkflowContext(subject, actorUserId, Set.of(), Map.of());
    }

    /**
     * Returns the subject cast to the expected type.
     *
     * @param type the expected aggregate type
     * @param <T> the type
     * @return the subject
     */
    public <T> T subjectAs(Class<T> type) {
        return type.cast(subject);
    }

    public Object subject() {
        return subject;
    }

    public UUID actorUserId() {
        return actorUserId;
    }

    public boolean hasScope(String scope) {
        return actorScopes.contains(scope);
    }

    public Object param(String key) {
        return params.get(key);
    }

    public WorkflowTransition transition() {
        return transition;
    }

    public WorkflowState fromState() {
        return transition == null ? null : transition.fromState();
    }

    public WorkflowState toState() {
        return transition == null ? null : transition.toState();
    }

    void bindTransition(WorkflowTransition resolved) {
        this.transition = resolved;
    }
}
