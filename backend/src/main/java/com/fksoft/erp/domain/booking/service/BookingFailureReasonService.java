package com.fksoft.erp.domain.booking.service;

import com.fksoft.erp.domain.booking.model.BookingFailureReason;
import com.fksoft.erp.domain.booking.repository.BookingFailureReasonRepository;
import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link BookingFailureReason} cadastro. */
@Service
public class BookingFailureReasonService extends AbstractReferenceDataService<BookingFailureReason> {

    public BookingFailureReasonService(BookingFailureReasonRepository repository) {
        super(repository, c -> BookingFailureReason.create(c.code(), c.label(), c.sortOrder()));
    }
}
