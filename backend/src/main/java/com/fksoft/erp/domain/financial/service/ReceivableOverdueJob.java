package com.fksoft.erp.domain.financial.service;

import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.model.ReceivableStatusChanged;
import com.fksoft.erp.domain.financial.repository.ReceivableRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily check that flags operational Receivables ({@code OPEN}/{@code PARTIALLY_PAID}) as {@code OVERDUE} once a
 * due date has passed with a balance remaining, and republishes the financial status so the source Commercial
 * Order reflects it. It owns the {@code OPEN}/{@code PARTIALLY_PAID} → {@code OVERDUE} transition (a due date
 * passing fires no other event). It is idempotent, creates no Commission, Payment or Customer Care data, and
 * never touches the Commercial Order's own lifecycle.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReceivableOverdueJob {

    private final ReceivableRepository receivables;
    private final ApplicationEventPublisher events;

    /** Runs the overdue check daily (default 03:00; override with {@code app.financial.overdue-cron}). */
    @Scheduled(cron = "${app.financial.overdue-cron:0 0 3 * * *}")
    @Transactional
    public void run() {
        markOverdue(LocalDate.now());
    }

    /**
     * Flags every operational, past-due Receivable as {@code OVERDUE} as of {@code today}, saving and
     * republishing the financial status for each that transitions. The reference date is a parameter so the
     * behavior is testable.
     *
     * @param today the reference (current) date
     * @return the number of Receivables transitioned to {@code OVERDUE}
     */
    @Transactional
    public int markOverdue(LocalDate today) {
        List<Receivable> candidates =
                receivables.findByStatusIn(List.of(ReceivableStatus.OPEN, ReceivableStatus.PARTIALLY_PAID));
        int transitioned = 0;
        for (Receivable receivable : candidates) {
            if (receivable.markOverdueIfPastDue(today)) {
                receivables.save(receivable);
                events.publishEvent(new ReceivableStatusChanged(
                        receivable.id(),
                        receivable.commercialOrderId(),
                        receivable.status().name()));
                transitioned++;
            }
        }
        if (transitioned > 0) {
            log.info("Overdue check flagged {} receivable(s) as OVERDUE as of {}", transitioned, today);
        }
        return transitioned;
    }
}
