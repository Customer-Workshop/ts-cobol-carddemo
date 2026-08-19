package com.carddemo.repository;
import com.carddemo.model.SecurityUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface SecurityUserRepository extends JpaRepository<SecurityUser, String> {
    Page<SecurityUser> findByUserIdGreaterThanEqual(String userId, Pageable pageable);
}
