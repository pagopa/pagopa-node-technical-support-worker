package it.gov.pagopa.nodetsworker.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaymentAttemptInfo extends BasePaymentAttemptInfo {

  private String paymentToken;
  private String ccp;
  private String pmReceipt;
  private String paymentMethod;
  private String touchPoint;
  private BigDecimal fee;
  private BigDecimal feeOrganization;

}
