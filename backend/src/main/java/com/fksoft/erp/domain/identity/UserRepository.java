package com.fksoft.erp.domain.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Command/read repository for {@link User} aggregates (Identity module). */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    List<User> findByActiveTrueOrderByUsernameAsc();
}
