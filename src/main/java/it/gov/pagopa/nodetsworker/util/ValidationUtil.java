package it.gov.pagopa.nodetsworker.util;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.DateRequest;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) > dateRangeLimit) {
            throw new AppException(
                    AppErrorCodeMessageEnum.INTERVAL_TOO_LARGE,
                    dateRangeLimit);
        }
        return DateRequest.builder().from(dateFrom).to(dateTo).build();
    }

}
