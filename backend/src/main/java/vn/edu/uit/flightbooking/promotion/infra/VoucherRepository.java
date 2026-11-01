package vn.edu.uit.flightbooking.promotion.infra;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.uit.flightbooking.promotion.domain.Voucher;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

	Optional<Voucher> findByCode(String code);

	boolean existsByCode(String code);

	List<Voucher> findAllByOrderByIdDesc();

	/** Khoá dòng khi Admin sửa, để Q-14 đang chạy không làm {@code used_count} vượt {@code usage_limit} mới. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Voucher v where v.id = :id")
	Optional<Voucher> findForUpdate(@Param("id") long id);

}
