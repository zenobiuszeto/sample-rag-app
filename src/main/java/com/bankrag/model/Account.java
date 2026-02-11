package com.bankrag.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", unique = true, nullable = false)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "account_name")
    private String accountName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(length = 3)
    private String currency = "USD";

    @Column(name = "interest_rate", precision = 5, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "credit_limit", precision = 15, scale = 2)
    private BigDecimal creditLimit;

    private String status = "ACTIVE";

    @Column(name = "opened_date", nullable = false)
    private LocalDate openedDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions = new ArrayList<>();

    public Account() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }

    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getOpenedDate() { return openedDate; }
    public void setOpenedDate(LocalDate openedDate) { this.openedDate = openedDate; }

    public LocalDate getClosedDate() { return closedDate; }
    public void setClosedDate(LocalDate closedDate) { this.closedDate = closedDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<Transaction> getTransactions() { return transactions; }
    public void setTransactions(List<Transaction> transactions) { this.transactions = transactions; }

    /**
     * Generates a natural language account summary for RAG embedding.
     */
    public String toSummaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append(accountType).append(" account ").append(accountNumber);
        sb.append(" for customer ").append(customer.getCustomerId());
        sb.append(" (").append(customer.getFullName()).append(")");
        sb.append(", balance: $").append(balance);
        if (interestRate != null) sb.append(", interest rate: ").append(interestRate).append("%");
        if (creditLimit != null) sb.append(", credit limit: $").append(creditLimit);
        sb.append(", status: ").append(status);
        sb.append(", opened: ").append(openedDate);
        return sb.toString();
    }
}
