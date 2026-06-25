package com.fksoft.erp.infra.web;

import static java.util.Map.entry;

import com.fksoft.erp.domain.booking.exception.BookingAttemptResultNotAvailableException;
import com.fksoft.erp.domain.booking.exception.BookingAttemptTypeNotAvailableException;
import com.fksoft.erp.domain.booking.exception.BookingFailureReasonNotAvailableException;
import com.fksoft.erp.domain.booking.exception.BookingItemAlreadyResolvedException;
import com.fksoft.erp.domain.booking.exception.BookingItemNotConfirmableException;
import com.fksoft.erp.domain.booking.exception.BookingItemNotFailableException;
import com.fksoft.erp.domain.booking.exception.BookingItemNotFoundException;
import com.fksoft.erp.domain.booking.exception.BookingItemNotMarkableException;
import com.fksoft.erp.domain.booking.exception.BookingOperatorNotFoundException;
import com.fksoft.erp.domain.booking.exception.BookingRequestAccessDeniedException;
import com.fksoft.erp.domain.booking.exception.BookingRequestAlreadyExistsException;
import com.fksoft.erp.domain.booking.exception.BookingRequestNotFoundException;
import com.fksoft.erp.domain.booking.exception.CommercialOrderNotPendingBookingException;
import com.fksoft.erp.domain.commission.exception.CommissionRuleDatesInvalidException;
import com.fksoft.erp.domain.commission.exception.CommissionRuleNotFoundException;
import com.fksoft.erp.domain.commission.exception.CommissionRulePercentageAboveLimitException;
import com.fksoft.erp.domain.commission.exception.CommissionRulePercentageInvalidException;
import com.fksoft.erp.domain.commission.exception.CommissionRuleTargetUserNotFoundException;
import com.fksoft.erp.domain.crm.exception.CustomerNotFoundException;
import com.fksoft.erp.domain.crm.exception.DuplicateLeadException;
import com.fksoft.erp.domain.crm.exception.InteractionResultNotAvailableException;
import com.fksoft.erp.domain.crm.exception.InteractionTypeNotAvailableException;
import com.fksoft.erp.domain.crm.exception.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.LeadAssignmentNotAllowedException;
import com.fksoft.erp.domain.crm.exception.LeadCannotBeMarkedLostException;
import com.fksoft.erp.domain.crm.exception.LeadCannotBeQualifiedException;
import com.fksoft.erp.domain.crm.exception.LeadContactRequiredException;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.LeadNotQualifiedForOpportunityException;
import com.fksoft.erp.domain.crm.exception.LeadQualificationRequiresResponsibleException;
import com.fksoft.erp.domain.crm.exception.LossReasonNotAvailableException;
import com.fksoft.erp.domain.crm.exception.OpportunityAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.OpportunityActivityResultNotAvailableException;
import com.fksoft.erp.domain.crm.exception.OpportunityActivityTypeNotAvailableException;
import com.fksoft.erp.domain.crm.exception.OpportunityAlreadyExistsForLeadException;
import com.fksoft.erp.domain.crm.exception.OpportunityCannotBeMarkedLostException;
import com.fksoft.erp.domain.crm.exception.OpportunityCannotBeMarkedWonException;
import com.fksoft.erp.domain.crm.exception.OpportunityLossReasonNotAvailableException;
import com.fksoft.erp.domain.crm.exception.OpportunityNotFoundException;
import com.fksoft.erp.domain.crm.exception.OpportunityStageTransitionException;
import com.fksoft.erp.domain.crm.exception.OriginNotAvailableException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.financial.exception.InstallmentNotPayableException;
import com.fksoft.erp.domain.financial.exception.InstallmentScheduleInvalidException;
import com.fksoft.erp.domain.financial.exception.OrderBookingNotConfirmedException;
import com.fksoft.erp.domain.financial.exception.PaymentAlreadyReversedException;
import com.fksoft.erp.domain.financial.exception.PaymentExceedsOutstandingException;
import com.fksoft.erp.domain.financial.exception.PaymentInstallmentNotFoundException;
import com.fksoft.erp.domain.financial.exception.PaymentMethodNotAvailableException;
import com.fksoft.erp.domain.financial.exception.PaymentNotFoundException;
import com.fksoft.erp.domain.financial.exception.ReceivableAccessDeniedException;
import com.fksoft.erp.domain.financial.exception.ReceivableAlreadyExistsException;
import com.fksoft.erp.domain.financial.exception.ReceivableNotFoundException;
import com.fksoft.erp.domain.financial.exception.SourceOrderAccessDeniedException;
import com.fksoft.erp.domain.financial.exception.SourceOrderNotFoundException;
import com.fksoft.erp.domain.identity.InvalidCredentialsException;
import com.fksoft.erp.domain.reference.DuplicateReferenceCodeException;
import com.fksoft.erp.domain.reference.ReferenceNotFoundException;
import com.fksoft.erp.domain.sales.exception.CommercialOrderAccessDeniedException;
import com.fksoft.erp.domain.sales.exception.CommercialOrderAlreadyExistsException;
import com.fksoft.erp.domain.sales.exception.CommercialOrderNotFoundException;
import com.fksoft.erp.domain.sales.exception.CustomerRejectionReasonNotAvailableException;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.exception.ProposalAccessDeniedException;
import com.fksoft.erp.domain.sales.exception.ProposalAlreadyExistsForOpportunityException;
import com.fksoft.erp.domain.sales.exception.ProposalDiscountInvalidException;
import com.fksoft.erp.domain.sales.exception.ProposalHasNoItemsException;
import com.fksoft.erp.domain.sales.exception.ProposalItemInvalidException;
import com.fksoft.erp.domain.sales.exception.ProposalItemNotFoundException;
import com.fksoft.erp.domain.sales.exception.ProposalItemTypeNotAvailableException;
import com.fksoft.erp.domain.sales.exception.ProposalNotAcceptedException;
import com.fksoft.erp.domain.sales.exception.ProposalNotApprovedException;
import com.fksoft.erp.domain.sales.exception.ProposalNotEditableException;
import com.fksoft.erp.domain.sales.exception.ProposalNotFoundException;
import com.fksoft.erp.domain.sales.exception.ProposalNotSentException;
import com.fksoft.erp.domain.sales.exception.ProposalNotUnderReviewException;
import com.fksoft.erp.domain.sales.exception.ProposalRejectionReasonNotAvailableException;
import com.fksoft.erp.domain.sales.exception.ProposalRejectionReasonRequiredException;
import com.fksoft.erp.domain.sales.exception.ProposalResponsibleRequiredException;
import com.fksoft.erp.domain.sales.exception.ProposalTotalRequiredException;
import com.fksoft.erp.domain.sales.exception.ProposalValidityRequiredException;
import com.fksoft.erp.domain.sales.exception.SendingChannelNotAvailableException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Registry mapping each {@link DomainException} type to its HTTP status. Keeping the mapping here
 * (presentation) keeps the domain free of transport concerns. A build-time test asserts every
 * domain exception subtype is mapped.
 */
@Component
public class HttpErrorMapping {

    private final Map<Class<? extends DomainException>, HttpStatus> mappings = Map.ofEntries(
            entry(LeadContactRequiredException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OriginNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ResponsiblePersonNotFoundException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(LeadNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(LeadAccessDeniedException.class, HttpStatus.FORBIDDEN),
            entry(LeadAssignmentNotAllowedException.class, HttpStatus.FORBIDDEN),
            entry(LeadCannotBeQualifiedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(LeadQualificationRequiresResponsibleException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(LeadCannotBeMarkedLostException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(LossReasonNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(InteractionTypeNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(InteractionResultNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(InvalidCredentialsException.class, HttpStatus.UNAUTHORIZED),
            entry(DuplicateReferenceCodeException.class, HttpStatus.CONFLICT),
            entry(DuplicateLeadException.class, HttpStatus.CONFLICT),
            entry(LeadNotQualifiedForOpportunityException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OpportunityAlreadyExistsForLeadException.class, HttpStatus.CONFLICT),
            entry(OpportunityNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(OpportunityAccessDeniedException.class, HttpStatus.FORBIDDEN),
            entry(OpportunityCannotBeMarkedLostException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OpportunityCannotBeMarkedWonException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OpportunityLossReasonNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OpportunityActivityTypeNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OpportunityActivityResultNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OpportunityStageTransitionException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OpportunityNotReadyForProposalException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalAlreadyExistsForOpportunityException.class, HttpStatus.CONFLICT),
            entry(ProposalNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(ProposalAccessDeniedException.class, HttpStatus.FORBIDDEN),
            entry(ProposalNotEditableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalItemNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(ProposalItemInvalidException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalDiscountInvalidException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalHasNoItemsException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalTotalRequiredException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalValidityRequiredException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalResponsibleRequiredException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalNotUnderReviewException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalRejectionReasonRequiredException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalRejectionReasonNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(CustomerRejectionReasonNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(SendingChannelNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalItemTypeNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalNotApprovedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalNotSentException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ProposalNotAcceptedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(CommercialOrderAlreadyExistsException.class, HttpStatus.CONFLICT),
            entry(CommercialOrderNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(CommercialOrderAccessDeniedException.class, HttpStatus.FORBIDDEN),
            entry(BookingRequestAlreadyExistsException.class, HttpStatus.CONFLICT),
            entry(BookingRequestNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(BookingRequestAccessDeniedException.class, HttpStatus.FORBIDDEN),
            entry(CommercialOrderNotPendingBookingException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(BookingOperatorNotFoundException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(BookingItemNotMarkableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(BookingItemNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(BookingItemNotConfirmableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(BookingItemAlreadyResolvedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(BookingItemNotFailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(BookingAttemptTypeNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(BookingAttemptResultNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(BookingFailureReasonNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(CustomerNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(OrderBookingNotConfirmedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(InstallmentScheduleInvalidException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ReceivableAlreadyExistsException.class, HttpStatus.CONFLICT),
            entry(ReceivableNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(ReceivableAccessDeniedException.class, HttpStatus.FORBIDDEN),
            entry(SourceOrderNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(SourceOrderAccessDeniedException.class, HttpStatus.FORBIDDEN),
            entry(PaymentMethodNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(PaymentInstallmentNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(InstallmentNotPayableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(PaymentExceedsOutstandingException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(PaymentNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(PaymentAlreadyReversedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(CommissionRuleNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(CommissionRulePercentageInvalidException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(CommissionRulePercentageAboveLimitException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(CommissionRuleDatesInvalidException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(CommissionRuleTargetUserNotFoundException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ReferenceNotFoundException.class, HttpStatus.NOT_FOUND));

    public HttpStatus statusFor(DomainException ex) {
        return mappings.getOrDefault(ex.getClass(), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public Set<Class<? extends DomainException>> mappedTypes() {
        return mappings.keySet();
    }
}
