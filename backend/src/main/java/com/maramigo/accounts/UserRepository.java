package com.maramigo.accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** @author Maram Abbas */
public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByIdAndEnabledTrue(Long id);
    boolean existsByRole(String role);
}
