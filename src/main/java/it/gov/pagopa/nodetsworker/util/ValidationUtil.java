package it.gov.pagopa.nodetsworker.util;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.DateRequest;

import java.time.LocalDate;

public class ValidationUtil {

    private ValidationUtil() {}

    /**
     * Check dates validity
     *
     * @param dateFrom
     * @param dateTo
     */
    public static DateRequest verifyDateRequest(LocalDate dateFrom, LocalDate dateTo, Integer dateRangeLimit) {
        if (dateFrom == null && dateTo != null || dateFrom != null && dateTo == null) {
            throw new AppException(
                    AppErrorCodeMessageEnum.POSITION_SERVICE_DATE_BAD_REQUEST,
                    "Date from and date to must be both defined");
        } else if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new AppException(
                    AppErrorCodeMessageEnum.POSITION_SERVICE_DATE_BAD_REQUEST,
                    "Date from must be before date to");
        }
        if (dateFrom == null && dateTo == null) {
            dateTo = LocalDate.now();
            dateFrom = dateTo.minusDays(dateRangeLimit);
        }
        return DateRequest.builder().from(dateFrom).to(dateTo).build();
    }

}
