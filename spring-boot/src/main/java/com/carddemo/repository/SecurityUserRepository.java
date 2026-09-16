package com.carddemo.repository;
import com.carddemo.model.SecurityUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface SecurityUserRepository extends JpaRepository<SecurityUser, String> {
    Page<SecurityUser> findByUserIdGreaterThanEqual(String userId, Pageable pageable);

    // S-12 keyed browse (COUSR00C STARTBR/READNEXT/READPREV): forward reads
    // the key itself plus look-ahead rows, backward reads predecessors desc.
    List<SecurityUser> findByUserIdGreaterThanEqualOrderByUserIdAsc(String userId,
                                                                  Pageable pageable);

    List<SecurityUser> findByUserIdLessThanOrderByUserIdDesc(String userId,
                                                           Pageable pageable);
}
