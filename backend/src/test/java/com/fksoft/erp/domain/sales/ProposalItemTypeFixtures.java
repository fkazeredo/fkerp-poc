package com.fksoft.erp.domain.sales;

import com.fksoft.erp.domain.sales.model.ProposalItemType;

/**
 * Shared test fixtures for the {@link ProposalItemType} cadastro (the former enum). Exposes the four seeded
 * reserved values as stable shared instances so domain tests that snapshot an item type through
 * Proposal → Order → Booking keep matching by reference (and so {@code requiresBooking} reflects the seeded
 * classification).
 */
public final class ProposalItemTypeFixtures {

    public static final ProposalItemType TRAVEL_PACKAGE =
            ProposalItemType.create("TRAVEL_PACKAGE", "Pacote de viagem", 1, true);
    public static final ProposalItemType CAR_RENTAL =
            ProposalItemType.create("CAR_RENTAL", "Locação de veículo", 2, true);
    public static final ProposalItemType SERVICE_FEE =
            ProposalItemType.create("SERVICE_FEE", "Taxa de serviço", 3, false);
    public static final ProposalItemType OTHER = ProposalItemType.create("OTHER", "Outro", 4, false);

    private ProposalItemTypeFixtures() {}
}
