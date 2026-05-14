package com.fdshield.repository;

import com.fdshield.entity.WithdrawalLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WithdrawalLogRepository extends JpaRepository<WithdrawalLog, Long> {
    List<WithdrawalLog> findByFixedDepositId(Long fdId);
}
