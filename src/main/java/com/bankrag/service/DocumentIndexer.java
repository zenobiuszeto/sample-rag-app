package com.bankrag.service;

import com.bankrag.model.Account;
import com.bankrag.model.Customer;
import com.bankrag.model.Transaction;
import com.bankrag.repository.AccountRepository;
import com.bankrag.repository.CustomerRepository;
import com.bankrag.repository.TransactionRepository;
import com.bankrag.repository.VectorStoreRepository;
import com.bankrag.repository.VectorStoreRepository.EmbeddingRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Indexes banking data into the vector store for RAG retrieval.
 * Creates embeddings for:
 * - Customer profiles
 * - Account summaries
 * - Transaction patterns (aggregated per account)
 * - Banking policies (static knowledge base)
 */
@Service
public class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final VectorStoreRepository vectorStoreRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentIndexer(CustomerRepository customerRepository,
                           AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           VectorStoreRepository vectorStoreRepository,
                           EmbeddingService embeddingService) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.vectorStoreRepository = vectorStoreRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Index all banking data. Idempotent — skips if embeddings exist.
     */
    public IndexStats indexAll() {
        long existingCount = vectorStoreRepository.countEmbeddings();
        if (existingCount > 0) {
            log.info("Embeddings already exist ({}), skipping indexing.", existingCount);
            return new IndexStats(0, 0, 0, 0, true);
        }

        log.info("Starting document indexing...");
        long start = System.currentTimeMillis();

        int profiles = indexCustomerProfiles();
        int accounts = indexAccountSummaries();
        int patterns = indexTransactionPatterns();
        int policies = indexBankingPolicies();

        long elapsed = System.currentTimeMillis() - start;
        log.info("Indexing complete in {}ms: {} profiles, {} accounts, {} patterns, {} policies",
                elapsed, profiles, accounts, patterns, policies);

        return new IndexStats(profiles, accounts, patterns, policies, false);
    }

    /**
     * Re-index everything (deletes existing embeddings first).
     */
    public IndexStats reindexAll() {
        log.info("Deleting all existing embeddings for re-index...");
        vectorStoreRepository.deleteAll();
        return indexAll();
    }

    private int indexCustomerProfiles() {
        log.info("Indexing customer profiles...");
        int indexed = 0;
        int pageSize = 500;
        int page = 0;

        while (true) {
            Page<Customer> customers = customerRepository.findAll(PageRequest.of(page, pageSize));
            if (customers.isEmpty()) break;

            List<EmbeddingRecord> records = new ArrayList<>();
            List<String> texts = new ArrayList<>();

            for (Customer c : customers) {
                String text = c.toProfileText();
                texts.add(text);
            }

            List<float[]> embeddings = embeddingService.embedBatch(texts);

            int i = 0;
            for (Customer c : customers) {
                String metadata = toJson(Map.of(
                    "customer_id", c.getCustomerId(),
                    "segment", c.getSegment(),
                    "risk_rating", c.getRiskRating(),
                    "credit_score", c.getCreditScore() != null ? c.getCreditScore() : 0
                ));
                records.add(new EmbeddingRecord(texts.get(i), "CUSTOMER_PROFILE",
                        c.getCustomerId(), metadata, embeddings.get(i)));
                i++;
            }

            vectorStoreRepository.batchInsertEmbeddings(records);
            indexed += records.size();
            page++;

            if (page % 5 == 0) {
                log.info("  Indexed {} customer profiles...", indexed);
            }
        }

        return indexed;
    }

    private int indexAccountSummaries() {
        log.info("Indexing account summaries...");
        int indexed = 0;
        int pageSize = 500;
        int page = 0;

        while (true) {
            Page<Account> accounts = accountRepository.findAll(PageRequest.of(page, pageSize));
            if (accounts.isEmpty()) break;

            List<EmbeddingRecord> records = new ArrayList<>();
            List<String> texts = new ArrayList<>();

            for (Account a : accounts) {
                String text = a.toSummaryText();
                texts.add(text);
            }

            List<float[]> embeddings = embeddingService.embedBatch(texts);

            int i = 0;
            for (Account a : accounts) {
                String metadata = toJson(Map.of(
                    "account_number", a.getAccountNumber(),
                    "account_type", a.getAccountType(),
                    "customer_id", a.getCustomer().getCustomerId(),
                    "balance", a.getBalance().toString(),
                    "status", a.getStatus()
                ));
                records.add(new EmbeddingRecord(texts.get(i), "ACCOUNT_SUMMARY",
                        a.getAccountNumber(), metadata, embeddings.get(i)));
                i++;
            }

            vectorStoreRepository.batchInsertEmbeddings(records);
            indexed += records.size();
            page++;
        }

        return indexed;
    }

    private int indexTransactionPatterns() {
        log.info("Indexing transaction patterns...");
        int indexed = 0;
        int pageSize = 500;
        int page = 0;

        while (true) {
            Page<Account> accounts = accountRepository.findAll(PageRequest.of(page, pageSize));
            if (accounts.isEmpty()) break;

            List<EmbeddingRecord> records = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            List<Account> validAccounts = new ArrayList<>();

            for (Account a : accounts) {
                List<Transaction> txns = transactionRepository.findByAccountId(a.getId());
                if (txns.isEmpty()) continue;

                String patternText = buildTransactionPattern(a, txns);
                texts.add(patternText);
                validAccounts.add(a);
            }

            if (!texts.isEmpty()) {
                List<float[]> embeddings = embeddingService.embedBatch(texts);

                for (int i = 0; i < validAccounts.size(); i++) {
                    Account a = validAccounts.get(i);
                    String metadata = toJson(Map.of(
                        "account_number", a.getAccountNumber(),
                        "customer_id", a.getCustomer().getCustomerId(),
                        "account_type", a.getAccountType()
                    ));
                    records.add(new EmbeddingRecord(texts.get(i), "TRANSACTION_PATTERN",
                            a.getAccountNumber(), metadata, embeddings.get(i)));
                }

                vectorStoreRepository.batchInsertEmbeddings(records);
                indexed += records.size();
            }

            page++;
            if (page % 10 == 0) {
                log.info("  Indexed {} transaction patterns...", indexed);
            }
        }

        return indexed;
    }

    private String buildTransactionPattern(Account account, List<Transaction> txns) {
        StringBuilder sb = new StringBuilder();
        sb.append("Transaction pattern for ").append(account.getAccountType())
          .append(" account ").append(account.getAccountNumber())
          .append(" (").append(account.getCustomer().getFullName()).append("): ");

        // Aggregate stats
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;
        Map<String, Integer> categoryCount = new HashMap<>();
        Map<String, BigDecimal> categorySpend = new HashMap<>();

        for (Transaction t : txns) {
            if ("DEPOSIT".equals(t.getTransactionType())) {
                totalDeposits = totalDeposits.add(t.getAmount());
            } else {
                totalWithdrawals = totalWithdrawals.add(t.getAmount());
            }
            if (t.getMerchantCategory() != null) {
                categoryCount.merge(t.getMerchantCategory(), 1, Integer::sum);
                categorySpend.merge(t.getMerchantCategory(), t.getAmount(), BigDecimal::add);
            }
        }

        sb.append(txns.size()).append(" transactions total. ");
        sb.append("Total deposits: $").append(totalDeposits).append(". ");
        sb.append("Total spending: $").append(totalWithdrawals).append(". ");

        // Top spending categories
        if (!categorySpend.isEmpty()) {
            sb.append("Top spending categories: ");
            categorySpend.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> sb.append(e.getKey()).append(" ($").append(e.getValue()).append("), "));
        }

        // Channel distribution
        Map<String, Long> channels = txns.stream()
            .collect(Collectors.groupingBy(Transaction::getChannel, Collectors.counting()));
        sb.append("Channels used: ");
        channels.forEach((ch, cnt) -> sb.append(ch).append(" (").append(cnt).append("), "));

        return sb.toString().trim();
    }

    private int indexBankingPolicies() {
        log.info("Indexing banking policies...");
        List<String[]> policies = List.of(
            new String[]{"overdraft-policy", "Overdraft Protection Policy: Customers with checking accounts are automatically enrolled in overdraft protection. The bank covers transactions up to $500 beyond the account balance. An overdraft fee of $35 is charged per occurrence. Premium customers receive up to $1,000 overdraft coverage with reduced $15 fees. Overdraft protection can be opted out by contacting customer service."},
            new String[]{"interest-rates", "Interest Rate Policy: Savings accounts earn between 2.0% and 5.0% APY depending on balance tier. Tier 1 ($0-$9,999): 2.0% APY. Tier 2 ($10,000-$49,999): 3.5% APY. Tier 3 ($50,000+): 5.0% APY. Checking accounts earn 0.1% APY. Interest is compounded daily and credited monthly."},
            new String[]{"fraud-detection", "Fraud Detection Policy: Transactions exceeding $10,000 require additional verification. International transactions trigger automated alerts. Multiple transactions at the same merchant within 5 minutes are flagged for review. Customers are notified via SMS and email for transactions over $500. Suspected fraud results in temporary account freeze pending investigation."},
            new String[]{"credit-card-rewards", "Credit Card Rewards Program: Standard cards earn 1% cashback on all purchases. Premium cards earn 2% on dining and travel, 1.5% on everything else. Private Banking clients earn 3% on all categories. Points can be redeemed for statement credits, travel, or gift cards. Points expire after 24 months of account inactivity."},
            new String[]{"loan-eligibility", "Loan Eligibility Requirements: Personal loans require minimum credit score of 620. Mortgage applications require minimum score of 680, 3% down payment for first-time buyers, 20% for investment properties. Auto loans available for scores 580+. Debt-to-income ratio must not exceed 43% for mortgages, 50% for personal loans."},
            new String[]{"customer-segments", "Customer Segmentation Policy: RETAIL segment: Standard banking services, accounts with balances under $100,000 combined. PREMIUM segment: Enhanced services, dedicated support line, fee waivers, combined balances $100,000-$1,000,000. PRIVATE_BANKING segment: Full-service wealth management, personal banker, exclusive investment products, combined balances over $1,000,000."},
            new String[]{"dispute-resolution", "Transaction Dispute Policy: Customers have 60 days from statement date to dispute transactions. Provisional credit is issued within 10 business days of filing. Investigation completes within 45 days for domestic transactions, 90 days for international. Disputes can be filed online, by phone, or at any branch location."},
            new String[]{"account-closure", "Account Closure Policy: Accounts can be closed at any time by the account holder. Early closure fee of $25 applies if account is less than 6 months old. All pending transactions must clear before closure. Remaining balance is mailed as check or transferred to another account. Closed accounts cannot be reopened after 90 days."},
            new String[]{"wire-transfer", "Wire Transfer Policy: Domestic wire transfers cost $25 for outgoing, free for incoming. International wires cost $45 outgoing, $15 incoming. Premium and Private Banking customers receive free domestic wires. Wire transfers initiated before 3 PM ET are processed same business day. Daily wire limit is $50,000 for retail, $250,000 for premium, unlimited for private banking."},
            new String[]{"aml-kyc", "Anti-Money Laundering and KYC Policy: All new accounts require government-issued ID and proof of address. Cash transactions over $10,000 are reported to FinCEN. Suspicious activity reports (SARs) are filed for unusual patterns. Enhanced due diligence required for high-risk customers and politically exposed persons. Customer information is reverified every 2 years."}
        );

        List<EmbeddingRecord> records = new ArrayList<>();
        List<String> texts = policies.stream().map(p -> p[1]).collect(Collectors.toList());
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        for (int i = 0; i < policies.size(); i++) {
            String metadata = toJson(Map.of("policy_id", policies.get(i)[0]));
            records.add(new EmbeddingRecord(
                policies.get(i)[1], "POLICY", policies.get(i)[0], metadata, embeddings.get(i)
            ));
        }

        vectorStoreRepository.batchInsertEmbeddings(records);
        return records.size();
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    public record IndexStats(int profiles, int accounts, int patterns, int policies, boolean skipped) {
        public int total() { return profiles + accounts + patterns + policies; }
    }
}
