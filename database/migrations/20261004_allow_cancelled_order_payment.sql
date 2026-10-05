-- Apply to existing PostgreSQL databases before running the updated application.
-- Fresh schemas derive the allowed values from order.model.PaymentStatus.
-- Only expands the allowed states; existing payment data is not rewritten.
BEGIN;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check
    CHECK (payment_status IN ('PENDING_PAYMENT', 'PAID', 'FAILED', 'REFUNDED', 'CANCELLED'));
COMMIT;
