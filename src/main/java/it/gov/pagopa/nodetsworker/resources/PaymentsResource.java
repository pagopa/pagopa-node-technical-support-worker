package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.observability.dto.PaymentDetailDto;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The observability read API ({@code /api/*}), implementing the functional contract in {@code mocks/}.
 */
@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsResource {

    @Inject
    ObservabilityService service;

    @GET
    @Path("/organizationFiscalCode/{paFiscalCode}/noticeNumber/{noticeNumber}")
    public PaymentDetailDto paymentDetail(
            @PathParam("paFiscalCode") String paFiscalCode,
            @PathParam("noticeNumber") String noticeNumber
    ) {
        if (!paFiscalCode.matches("\\d{11}") || !noticeNumber.matches("\\d{18}")) {
            throw new AppException(AppErrorCodeMessageEnum.BAD_REQUEST, "Invalid organization fiscal code and notice number");
        }
        return service.paymentDetail(paFiscalCode, noticeNumber);
    }
}
