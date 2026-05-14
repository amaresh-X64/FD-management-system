package com.fdmanagement.repository;

import com.fdmanagement.entity.FixedDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FixedDepositRepository extends JpaRepository<FixedDeposit, Long> {

    List<FixedDeposit> findByUserIdAndStatus(Long userId, String status);

    List<FixedDeposit> findByUserId(Long userId);

    @Query("SELECT fd FROM FixedDeposit fd WHERE fd.user.id = :userId AND fd.status = 'ACTIVE'")
    List<FixedDeposit> findActiveFdsByUserId(@Param("userId") Long userId);
}
