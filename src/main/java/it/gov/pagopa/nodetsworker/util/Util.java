package it.gov.pagopa.nodetsworker.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public class Util {



  public static String format(LocalDate d) {
    return d.format(DateTimeFormatter.ISO_DATE);
  }
}
