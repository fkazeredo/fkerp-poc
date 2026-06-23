package com.fksoft.erp.domain.workflow;

/**
 * Catalog SPI for a transition <em>guard</em> (a condition or a validator): runs before the state changes
 * and throws a {@link com.fksoft.erp.domain.error.DomainException} to block the transition. Each
 * implementation is a Spring bean identified by its {@link #key()}, which a {@link WorkflowRule} references.
 * Guards live in the owning domain (e.g. {@code domain.crm.workflow}); the kernel only knows the SPI.
 */
public interface WorkflowGuard {

    /**
     * The stable catalog key a {@link WorkflowRule} references this guard by.
     *
     * @return the key
     */
    String key();

    /**
     * Checks the guard, throwing a domain exception to block the transition when it does not hold.
     *
     * @param ctx the transition context
     */
    void check(WorkflowContext ctx);
}
