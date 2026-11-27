package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.observability.dto.PaymentInfoDto;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Dedicated payment-info API: the payment detail projection including the raw payment token of each
 * attempt. The observability API ({@code /api/*}) keeps exposing only the masked {@code tokenDisplay}.
 */
@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsResource {

    @Inject
    ObservabilityService service;

    @GET
    @Path("/organizationFiscalCode/{paFiscalCode}/noticeNumber/{noticeNumber}")
    public PaymentInfoDto paymentDetail(
            @PathParam("paFiscalCode") String paFiscalCode,
            @PathParam("noticeNumber") String noticeNumber
    ) {
        if (!paFiscalCode.matches("\\d{11}") || !noticeNumber.matches("\\d{18}")) {
            throw new AppException(AppErrorCodeMessageEnum.BAD_REQUEST, "Invalid organization fiscal code and notice number");
        }
        return service.paymentInfo(paFiscalCode, noticeNumber);
    }
}
