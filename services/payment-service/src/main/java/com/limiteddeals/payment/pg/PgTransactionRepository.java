package com.limiteddeals.payment.pg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PgTransactionRepository extends JpaRepository<PgTransaction, String> {

    Optional<PgTransaction> findByMerchantOrderKey(String merchantOrderKey);

    List<PgTransaction> findByResult(PgTransaction.Result result);
}
