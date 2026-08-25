package com.familyti.product.repository;

import com.familyti.product.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    UserDetails findByEmail(String email);

}
