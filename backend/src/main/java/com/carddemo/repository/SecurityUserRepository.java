package com.carddemo.repository;

import com.carddemo.model.SecurityUser;
import org.springframework.data.jpa.repository.JpaRepository;

/** B-0006 USRSEC: READ by SEC-USR-ID (COSGN00C.cbl:211-219) -> findById. */
public interface SecurityUserRepository extends JpaRepository<SecurityUser, String> {
}
