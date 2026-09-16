package com.carddemo.repository;

import com.carddemo.model.AuthFraud;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AUTHFRDS journal access (S19-B5): the primary-key read drives the
 * insert-vs-update upsert that replaces SQLCODE -803 handling.
 */
public interface AuthFraudRepository extends JpaRepository<AuthFraud, AuthFraud.Id> {
}
