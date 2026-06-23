package com.fksoft.erp.domain.workflow;

/**
 * Catalog SPI for a transition <em>post function</em>: runs after the state changes, performing a side
 * effect (recording history, enriching the aggregate, publishing a domain event, consolidating a parent).
 * Each implementation is a Spring bean identified by its {@link #key()}, referenced by a {@link WorkflowRule}.
 * Post functions live in the owning domain (e.g. {@code domain.sales.workflow}); the kernel only knows the SPI.
 */
public interface WorkflowPostFunction {

    /**
     * The stable catalog key a {@link WorkflowRule} references this post function by.
     *
     * @return the key
     */
    String key();

    /**
     * Applies the side effect for the (already validated) transition.
     *
     * @param ctx the transition context
     */
    void apply(WorkflowContext ctx);
}
