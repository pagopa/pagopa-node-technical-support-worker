package it.gov.pagopa.nodetsworker.exceptions;

import lombok.Getter;

import javax.validation.constraints.NotNull;

@Getter
public class AppException extends RuntimeException{
    
    String title;

    int httpStatus;

    public AppException(@NotNull AppError appError, Object... args) {
        super(formatDetails(appError, args));
        this.httpStatus = appError.httpStatus;
        this.title = appError.title;
    }

    private static String formatDetails(AppError appError, Object[] args) {
        return String.format(appError.details, args);
    }
}
