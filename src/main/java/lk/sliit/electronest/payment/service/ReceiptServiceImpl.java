package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.payment.model.Receipt;
import lk.sliit.electronest.payment.repository.ReceiptRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;

    public ReceiptServiceImpl(ReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    @Override
    public Receipt createReceipt(Receipt receipt) {

        // Generate receipt number
        receipt.setReceiptNumber(
                "REC-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase()
        );

        // Set issued time
        receipt.setIssuedAt(LocalDateTime.now());

        // Set default amounts
        if (receipt.getTaxAmount() == null) {
            receipt.setTaxAmount(java.math.BigDecimal.ZERO);
        }

        if (receipt.getShippingFee() == null) {
            receipt.setShippingFee(java.math.BigDecimal.ZERO);
        }

        if (receipt.getDiscountAmount() == null) {
            receipt.setDiscountAmount(java.math.BigDecimal.ZERO);
        }

        return receiptRepository.save(receipt);
    }

    @Override
    public Receipt getReceiptById(Long id) {

        return receiptRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Receipt not found with ID: " + id
                        )
                );
    }

    @Override
    public Receipt getReceiptByReceiptNumber(String receiptNumber) {

        return receiptRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Receipt not found with receipt number: "
                                        + receiptNumber
                        )
                );
    }

    @Override
    public Receipt getReceiptByPaymentId(Long paymentId) {

        return receiptRepository.findByPaymentId(paymentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Receipt not found for payment ID: "
                                        + paymentId
                        )
                );
    }

    @Override
    public Receipt getReceiptByTransactionId(String transactionId) {

        return receiptRepository.findByPayment_TransactionId(transactionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Receipt not found for transaction ID: "
                                        + transactionId
                        )
                );
    }

    @Override
    public List<Receipt> getAllReceipts() {

        return receiptRepository.findAll();
    }
}