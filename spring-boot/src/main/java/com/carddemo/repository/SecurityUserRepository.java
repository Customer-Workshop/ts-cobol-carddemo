package com.carddemo.repository;
import com.carddemo.model.SecurityUser;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SecurityUserRepository extends JpaRepository<SecurityUser, String> {}
