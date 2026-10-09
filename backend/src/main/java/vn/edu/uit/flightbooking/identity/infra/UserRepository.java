package vn.edu.uit.flightbooking.identity.infra;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.identity.domain.User;
import vn.edu.uit.flightbooking.identity.domain.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	@Query("SELECT u.status FROM User u WHERE u.id = :id")
	Optional<UserStatus> findStatusById(long id);

	/** FR-110: {@code pattern} dạng {@code %chuỗi thường%}, khớp email hoặc họ tên; {@code role} null là mọi vai trò. */
	@Query("""
			SELECT u FROM User u
			WHERE (:role IS NULL OR u.role = :role)
			  AND (lower(u.email) LIKE :pattern OR lower(u.fullName) LIKE :pattern)
			ORDER BY u.id DESC
			""")
	Page<User> search(String pattern, Role role, Pageable pageable);

}
