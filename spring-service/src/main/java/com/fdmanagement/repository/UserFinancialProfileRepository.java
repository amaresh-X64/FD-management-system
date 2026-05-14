package com.fdmanagement.repository;

import com.fdmanagement.entity.UserFinancialProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFinancialProfileRepository extends JpaRepository<UserFinancialProfile, Long> {
}
