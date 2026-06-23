package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.booking.model.BookingAttemptResult;
import com.fksoft.erp.domain.booking.service.BookingAttemptResultService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the BookingAttemptResult cadastro. */
@RestController
@RequestMapping("/api/booking/booking-attempt-results")
public class BookingAttemptResultController extends AbstractReferenceController<BookingAttemptResult> {

    public BookingAttemptResultController(BookingAttemptResultService service) {
        super(service);
    }
}
