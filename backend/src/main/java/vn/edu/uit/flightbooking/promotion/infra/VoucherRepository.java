package vn.edu.uit.flightbooking.promotion.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.flightbooking.promotion.domain.Voucher;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

	Optional<Voucher> findByCode(String code);

}
