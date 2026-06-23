package com.fksoft.erp.domain.booking.service;

import com.fksoft.erp.domain.booking.model.BookingAttemptType;
import com.fksoft.erp.domain.booking.repository.BookingAttemptTypeRepository;
import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link BookingAttemptType} cadastro. */
@Service
public class BookingAttemptTypeService extends AbstractReferenceDataService<BookingAttemptType> {

    public BookingAttemptTypeService(BookingAttemptTypeRepository repository) {
        super(repository, c -> BookingAttemptType.create(c.code(), c.label(), c.sortOrder()));
    }
}
