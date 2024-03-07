package it.gov.pagopa.nodetsworker.util;

public class AppDBUtil {

  private AppDBUtil() {
    throw new IllegalStateException("Utility class");
  }

  public static int getPageCount( int count, int maxRecordsPerPage ){
    int fullyFilledPages = count / maxRecordsPerPage;
    int isPartialPage = ((count % maxRecordsPerPage) > 0)?(1):(0);
    return fullyFilledPages + isPartialPage;
  }

}
