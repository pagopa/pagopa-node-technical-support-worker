package it.gov.pagopa.nodetsworker.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Util {



  public static String format(LocalDate d) {
    return d.format(DateTimeFormatter.ISO_DATE);
  }
}
