package com.bankrag.repository;

import com.bankrag.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerId(String customerId);

    List<Customer> findBySegment(String segment);

    List<Customer> findByRiskRating(String riskRating);

    @Query("SELECT c FROM Customer c WHERE c.creditScore BETWEEN :minScore AND :maxScore")
    List<Customer> findByCreditScoreRange(int minScore, int maxScore);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.segment = :segment")
    long countBySegment(String segment);
}
