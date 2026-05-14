package com.fdshield.repository;

import com.fdshield.entity.UserFinancialProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFinancialProfileRepository extends JpaRepository<UserFinancialProfile, Long> {
}
