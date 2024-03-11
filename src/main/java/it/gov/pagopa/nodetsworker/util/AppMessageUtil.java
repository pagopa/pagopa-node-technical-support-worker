package it.gov.pagopa.nodetsworker.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public class AppMessageUtil {

  private static final String MESSAGES = "messages";

  private static ResourceBundle getBundle(Locale locale) {
    return Optional.ofNullable(locale)
        .map(localez -> ResourceBundle.getBundle(MESSAGES, localez))
        .orElse(ResourceBundle.getBundle(MESSAGES));
  }

  public static String getMessage(String messageKey, Object... messageArguments) {
    return getMessage(messageKey, null, messageArguments);
  }

  public static String getMessage(String messageKey, Locale locale, Object... messageArguments) {
    return MessageFormat.format(getMessage(messageKey, locale), messageArguments);
  }

  public static String getMessage(String messageKey) {
    return getMessage(messageKey, (Locale) null);
  }

  public static String getMessage(String messageKey, Locale locale) {
    try {
      return getBundle(locale).getString(messageKey);
    } catch (Exception e) {
      return messageKey;
    }
  }
}
