package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.booking.model.BookingFailureReason;
import com.fksoft.erp.domain.booking.service.BookingFailureReasonService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the BookingFailureReason cadastro. */
@RestController
@RequestMapping("/api/booking/booking-failure-reasons")
public class BookingFailureReasonController extends AbstractReferenceController<BookingFailureReason> {

    public BookingFailureReasonController(BookingFailureReasonService service) {
        super(service);
    }
}
