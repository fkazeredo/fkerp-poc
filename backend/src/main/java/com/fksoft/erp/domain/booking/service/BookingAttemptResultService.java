package com.fksoft.erp.domain.booking.service;

import com.fksoft.erp.domain.booking.model.BookingAttemptResult;
import com.fksoft.erp.domain.booking.repository.BookingAttemptResultRepository;
import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link BookingAttemptResult} cadastro. */
@Service
public class BookingAttemptResultService extends AbstractReferenceDataService<BookingAttemptResult> {

    public BookingAttemptResultService(BookingAttemptResultRepository repository) {
        super(repository, c -> BookingAttemptResult.create(c.code(), c.label(), c.sortOrder()));
    }
}
