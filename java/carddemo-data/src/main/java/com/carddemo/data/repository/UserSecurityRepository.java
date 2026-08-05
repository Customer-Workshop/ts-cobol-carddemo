package com.carddemo.data.repository;

import com.carddemo.data.entity.UserSecurity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSecurityRepository extends JpaRepository<UserSecurity, String> {}
