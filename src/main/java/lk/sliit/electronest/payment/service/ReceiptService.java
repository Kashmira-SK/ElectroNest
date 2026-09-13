package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.payment.model.Receipt;

import java.util.List;

public interface ReceiptService {

    Receipt createReceipt(Receipt receipt);

    Receipt getReceiptById(Long id);

    Receipt getReceiptByReceiptNumber(String receiptNumber);

    Receipt getReceiptByPaymentId(Long paymentId);

    Receipt getReceiptByTransactionId(String transactionId);

    List<Receipt> getAllReceipts();
}