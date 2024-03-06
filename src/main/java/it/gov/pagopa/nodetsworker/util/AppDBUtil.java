package it.gov.pagopa.nodetsworker.util;

public class AppDBUtil {

  private AppDBUtil() {
    throw new IllegalStateException("Utility class");
  }

//  public static Sort getSort(List<String> sortColumn) {
//    Sort sort = Sort.empty();
//    if (sortColumn != null && !sortColumn.isEmpty()) {
//      sortColumn.stream()
//          .filter(s -> s.replace(",", "").isBlank())
//          .forEach(
//              a -> {
//                String[] split = a.split(",");
//                String column = split[0].trim();
//                String direction = split[1].trim();
//                if (!column.isBlank()) {
//                  if (direction.equalsIgnoreCase("asc")) {
//                    sort.and(column, Direction.Ascending);
//                  } else if (direction.equalsIgnoreCase("desc")) {
//                    sort.and(column, Direction.Descending);
//                  } else {
//                    sort.and(column);
//                  }
//                }
//              });
//    }
//    return sort;
//  }
}
