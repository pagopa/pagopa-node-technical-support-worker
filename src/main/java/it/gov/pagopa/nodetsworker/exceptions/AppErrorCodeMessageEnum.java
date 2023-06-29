package it.gov.pagopa.nodetsworker.exceptions;

import it.gov.pagopa.nodetsworker.util.AppConstant;
import it.gov.pagopa.nodetsworker.util.AppMessageUtil;
import org.jboss.resteasy.reactive.RestResponse;

public enum AppErrorCodeMessageEnum implements AppErrorCodeMessageInterface {
  POSITION_SERVICE_DATE_BAD_REQUEST("0400","bad.request", RestResponse.Status.BAD_REQUEST),
  ERROR("0500", "system.error", RestResponse.Status.INTERNAL_SERVER_ERROR),
  BAD_REQUEST("0400", "bad.request", RestResponse.Status.BAD_REQUEST),
  BAD_REQUEST_INPUT_JSON("0401", "bad.request.inputJson", RestResponse.Status.BAD_REQUEST),
  BAD_REQUEST_INPUT_JSON_INSTANT(
      "0402", "bad.request.inputJson.instant", RestResponse.Status.BAD_REQUEST),
  BAD_REQUEST_INPUT_JSON_ENUM(
      "0403", "bad.request.inputJson.enum", RestResponse.Status.BAD_REQUEST),

  BAD_REQUEST_INPUT_JSON_DESERIALIZE_ERROR(
      "0404", "bad.request.inputJson.deserialize", RestResponse.Status.BAD_REQUEST),
  BAD_REQUEST_INPUT_JSON_NON_VALID_FORMAT(
      "0405", "bad.request.inputJson.notValidJsonFormat", RestResponse.Status.BAD_REQUEST),

  REPORTING_FLOW_NOT_FOUND("0701", "reporting-flow.notFound", RestResponse.Status.NOT_FOUND),
  REPORTING_FLOW_ALREADY_EXIST(
      "0702", "reporting-flow.alreadyExist", RestResponse.Status.BAD_REQUEST),
  REPORTING_FLOW_WRONG_ACTION(
      "0703", "reporting-flow.wrongAction", RestResponse.Status.BAD_REQUEST),
  REPORTING_FLOW_PSP_ID_NOT_MATCH(
      "0704", "reporting-flow.pspId.notMatch", RestResponse.Status.BAD_REQUEST),
  REPORTING_FLOW_PAYMENT_SAME_INDEX_IN_SAME_REQUEST(
      "0705", "reporting-flow.sameIndexInSameRequest", RestResponse.Status.BAD_REQUEST),
  REPORTING_FLOW_PAYMENT_DUPLICATE_INDEX(
      "0706", "reporting-flow.duplicateIndex", RestResponse.Status.BAD_REQUEST),
  REPORTING_FLOW_PAYMENT_NO_MATCH_INDEX(
      "0707", "reporting-flow.noMatchIndex", RestResponse.Status.BAD_REQUEST),
  PSP_UNKNOWN("0708", "pspId.unknown", RestResponse.Status.BAD_REQUEST),
  PSP_NOT_ENABLED("0709", "pspId.notEnabled", RestResponse.Status.BAD_REQUEST),
  BROKER_UNKNOWN("0710", "brokerId.unknown", RestResponse.Status.BAD_REQUEST),
  BROKER_NOT_ENABLED("0711", "brokerId.notEnabled", RestResponse.Status.BAD_REQUEST),
  CHANNEL_UNKNOWN("0712", "channelId.unknown", RestResponse.Status.BAD_REQUEST),
  CHANNEL_NOT_ENABLED("0713", "channelId.notEnabled", RestResponse.Status.BAD_REQUEST),
  CHANNEL_BROKER_WRONG_CONFIG(
      "0714", "channel.broker.wrongConfig", RestResponse.Status.BAD_REQUEST),
  CHANNEL_PSP_WRONG_CONFIG("0715", "channel.psp.wrongConfig", RestResponse.Status.BAD_REQUEST),
  EC_UNKNOWN("0716", "ecId.unknown", RestResponse.Status.BAD_REQUEST),
  EC_NOT_ENABLED("0717", "ecId.notEnabled", RestResponse.Status.BAD_REQUEST),
  REPORTING_FLOW_NAME_DATE_WRONG_FORMAT(
      "0718", "reporting-flow.name-date.wrongFormat", RestResponse.Status.BAD_REQUEST),
  REPORTING_FLOW_NAME_PSP_WRONG_FORMAT(
      "0719", "reporting-flow.name-psp.wrongFormat", RestResponse.Status.BAD_REQUEST);
  private final String errorCode;
  private final String errorMessageKey;
  private final RestResponse.Status httpStatus;

  AppErrorCodeMessageEnum(
      String errorCode, String errorMessageKey, RestResponse.Status httpStatus) {
    this.errorCode = errorCode;
    this.errorMessageKey = errorMessageKey;
    this.httpStatus = httpStatus;
  }

  @Override
  public String errorCode() {
    return AppConstant.SERVICE_CODE_APP + "-" + errorCode;
  }

  @Override
  public String message(Object... args) {
    return AppMessageUtil.getMessage(errorMessageKey, args);
  }

  @Override
  public RestResponse.Status httpStatus() {
    return httpStatus;
  }
}
