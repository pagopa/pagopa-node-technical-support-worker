INSERT INTO v_position_payment_status_snapshot
(id, pa_fiscal_code, notice_id, creditor_reference_id, payment_token, status, inserted_timestamp, updated_timestamp, fk_position_payment, inserted_by, updated_by)
VALUES(9000001, '12345678900', '001000000000000001', '000000000000001', 'ndp001offlinepaymenttoken0000001', 'PAID', '2023-01-30 10:00:00.000', '2023-01-30 10:00:01.000', 9000000, 'activatePaymentNotice', 'sendPaymentOutcome');
