package com.bankrag.repository;

import com.bankrag.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountId(Long accountId);

    List<Transaction> findByTransactionType(String transactionType);

    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId AND t.transactionDate BETWEEN :start AND :end ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountAndDateRange(Long accountId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM Transaction t WHERE t.amount > :threshold ORDER BY t.amount DESC")
    List<Transaction> findLargeTransactions(BigDecimal threshold);

    @Query("SELECT t.merchantCategory, SUM(t.amount) FROM Transaction t WHERE t.account.id = :accountId GROUP BY t.merchantCategory ORDER BY SUM(t.amount) DESC")
    List<Object[]> getSpendingByCategory(Long accountId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.id = :accountId")
    long countByAccountId(Long accountId);
}
