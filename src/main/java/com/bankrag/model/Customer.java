package com.bankrag.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", unique = true, nullable = false)
    private String customerId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "ssn_last4")
    private String ssnLast4;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_city")
    private String addressCity;

    @Column(name = "address_state")
    private String addressState;

    @Column(name = "address_zip")
    private String addressZip;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "customer_since", nullable = false)
    private LocalDate customerSince;

    private String segment;

    @Column(name = "risk_rating")
    private String riskRating;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Account> accounts = new ArrayList<>();

    // Constructors
    public Customer() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getSsnLast4() { return ssnLast4; }
    public void setSsnLast4(String ssnLast4) { this.ssnLast4 = ssnLast4; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressCity() { return addressCity; }
    public void setAddressCity(String addressCity) { this.addressCity = addressCity; }

    public String getAddressState() { return addressState; }
    public void setAddressState(String addressState) { this.addressState = addressState; }

    public String getAddressZip() { return addressZip; }
    public void setAddressZip(String addressZip) { this.addressZip = addressZip; }

    public Integer getCreditScore() { return creditScore; }
    public void setCreditScore(Integer creditScore) { this.creditScore = creditScore; }

    public LocalDate getCustomerSince() { return customerSince; }
    public void setCustomerSince(LocalDate customerSince) { this.customerSince = customerSince; }

    public String getSegment() { return segment; }
    public void setSegment(String segment) { this.segment = segment; }

    public String getRiskRating() { return riskRating; }
    public void setRiskRating(String riskRating) { this.riskRating = riskRating; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<Account> getAccounts() { return accounts; }
    public void setAccounts(List<Account> accounts) { this.accounts = accounts; }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Generates a natural language profile for RAG embedding.
     */
    public String toProfileText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Customer ").append(customerId).append(": ");
        sb.append(firstName).append(" ").append(lastName);
        sb.append(", email: ").append(email);
        if (phone != null) sb.append(", phone: ").append(phone);
        if (dateOfBirth != null) sb.append(", DOB: ").append(dateOfBirth);
        if (addressCity != null) sb.append(", location: ").append(addressCity).append(", ").append(addressState).append(" ").append(addressZip);
        if (creditScore != null) sb.append(", credit score: ").append(creditScore);
        sb.append(", customer since: ").append(customerSince);
        sb.append(", segment: ").append(segment);
        sb.append(", risk rating: ").append(riskRating);
        return sb.toString();
    }
}
