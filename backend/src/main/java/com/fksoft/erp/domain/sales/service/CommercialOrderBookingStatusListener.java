package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.booking.model.BookingStatusConsolidated;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reflects the Booking Operations context's consolidated booking status onto the source Commercial Order,
 * keeping the Order <b>owned by Sales &amp; Proposals</b>: Booking publishes the business fact
 * ({@link BookingStatusConsolidated}) and this Sales-owned listener writes the Order's own
 * {@code bookingStatus}. The reaction is synchronous, so it participates in the publisher's transaction and
 * commits atomically with the booking change. It never changes the Order's lifecycle, never cancels the Order,
 * and creates no Receivable, Payment or Commission data.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommercialOrderBookingStatusListener {

    private final CommercialOrderRepository orders;

    /**
     * Applies the consolidated booking status to the source Commercial Order. A missing Order is ignored
     * defensively (a Booking Request always references a real Order).
     *
     * @param event the booking-status consolidation fact (carries the order id and the consolidated status)
     */
    @EventListener
    public void on(BookingStatusConsolidated event) {
        orders.findById(event.commercialOrderId())
                .ifPresentOrElse(
                        order -> {
                            order.reflectBookingStatus(event.status());
                            orders.save(order);
                        },
                        () -> log.warn(
                                "Booking status {} consolidated for unknown Commercial Order {}",
                                event.status(),
                                event.commercialOrderId()));
    }
}
