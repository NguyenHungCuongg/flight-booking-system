package vn.edu.uit.flightbooking.identity.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.flightbooking.identity.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

}
