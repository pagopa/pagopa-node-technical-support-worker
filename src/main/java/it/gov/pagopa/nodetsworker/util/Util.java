package it.gov.pagopa.nodetsworker.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public class Util {
  public static String ifNotNull(Object o, String s) {
    if (o != null) {
      return s;
    } else {
      return "";
    }
  }

  public static void ifNotNull(Object o, Function<Void, Void> func) {
    if (o != null) {
      func.apply(null);
    }
  }

  public static Long toMillis(LocalDateTime d) {
    return d.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  public static String format(LocalDate d) {
    return d.format(DateTimeFormatter.ISO_DATE);
  }
}
