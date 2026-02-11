package com.bankrag.repository;

import com.bankrag.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerId(Long customerId);

    List<Account> findByAccountType(String accountType);

    List<Account> findByStatus(String status);

    @Query("SELECT a FROM Account a WHERE a.balance > :threshold AND a.accountType = :type")
    List<Account> findHighBalanceAccounts(BigDecimal threshold, String type);

    @Query("SELECT AVG(a.balance) FROM Account a WHERE a.accountType = :type")
    BigDecimal getAverageBalanceByType(String type);
}
