package com.carddemo.repository;

import com.carddemo.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

/** B-0002 CUSTDAT: READ by CUST-ID (COACTVWC.cbl:826-834) -> findById. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
