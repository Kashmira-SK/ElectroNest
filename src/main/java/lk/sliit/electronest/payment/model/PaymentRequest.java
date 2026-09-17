package lk.sliit.electronest.payment.model;

import java.math.BigDecimal;

public class PaymentRequest {

    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private String customerEmail;
    private String customerName;

    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;

    private String cardNumber;
    private String cardHolderName;
    private String expiryDate;
    private String cvv;
    private Long savedCardId;
    private boolean saveCard;

    public Long getSavedCardId() { return savedCardId; }
    public void setSavedCardId(Long savedCardId) { this.savedCardId = savedCardId; }
    public boolean isSaveCard() { return saveCard; }
    public void setSaveCard(boolean saveCard) { this.saveCard = saveCard; }

    private String deliveryAddress;
    private String itemizedSummary;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getCvv() {
        return cvv;
    }

    public void setCvv(String cvv) {
        this.cvv = cvv;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getItemizedSummary() {
        return itemizedSummary;
    }

    public void setItemizedSummary(String itemizedSummary) {
        this.itemizedSummary = itemizedSummary;
    }
}
