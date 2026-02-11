package com.bankrag.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 500)
    private String description;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "merchant_category")
    private String merchantCategory;

    private String channel;

    private String status = "COMPLETED";

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "posted_date")
    private LocalDateTime postedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Transaction() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public String getMerchantCategory() { return merchantCategory; }
    public void setMerchantCategory(String merchantCategory) { this.merchantCategory = merchantCategory; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    public LocalDateTime getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDateTime postedDate) { this.postedDate = postedDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Generates a natural language description for RAG embedding.
     */
    public String toDescriptionText() {
        StringBuilder sb = new StringBuilder();
        sb.append(transactionType).append(" of $").append(amount);
        sb.append(" on account ").append(account.getAccountNumber());
        sb.append(" on ").append(transactionDate.toLocalDate());
        if (merchantName != null) sb.append(" at ").append(merchantName);
        if (merchantCategory != null) sb.append(" (").append(merchantCategory).append(")");
        if (description != null) sb.append(": ").append(description);
        sb.append(" via ").append(channel);
        sb.append(", status: ").append(status);
        if (balanceAfter != null) sb.append(", balance after: $").append(balanceAfter);
        return sb.toString();
    }
}
