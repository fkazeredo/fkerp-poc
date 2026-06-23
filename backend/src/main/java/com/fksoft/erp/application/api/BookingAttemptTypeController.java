package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.booking.model.BookingAttemptType;
import com.fksoft.erp.domain.booking.service.BookingAttemptTypeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the BookingAttemptType cadastro. */
@RestController
@RequestMapping("/api/booking/booking-attempt-types")
public class BookingAttemptTypeController extends AbstractReferenceController<BookingAttemptType> {

    public BookingAttemptTypeController(BookingAttemptTypeService service) {
        super(service);
    }
}
