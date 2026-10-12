package vn.edu.uit.flightbooking.promotion;

import java.time.Instant;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.promotion.domain.Voucher;
import vn.edu.uit.flightbooking.promotion.infra.VoucherRepository;

/**
 * API công khai của module promotion (TDD §3.2): kiểm tra voucher, giữ và trả lượt dùng. Booking (Plan 10, 11) gọi
 * trong transaction của mình; giữ lượt sau cùng vì {@code vouchers} khoá sau cùng (TDD §5.4). Giữ và trả lượt chỉ bằng
 * {@code UPDATE} nguyên tử qua {@link JdbcClient} như {@code SeatInventory}, để không đụng persistence context của bên
 * gọi.
 */
@Service
public class VoucherApi {

	/** Kết quả {@link #check}: {@code voucherId} để ghi vào booking, {@code discount} là mức giảm theo BR-31. */
	public record Applied(long voucherId, String code, long discount) {
	}

	private final VoucherRepository vouchers;

	private final JdbcClient jdbc;

	VoucherApi(VoucherRepository vouchers, JdbcClient jdbc) {
		this.vouchers = vouchers;
		this.jdbc = jdbc;
	}

	/**
	 * BR-30 điều kiện 1–4 và BR-31 cho đơn có tạm tính {@code subtotal}. Chỉ đọc, chưa giữ lượt. Mã không phân biệt
	 * hoa thường và bỏ khoảng trắng hai đầu. Không dùng được thì ném VOUCHER_INVALID kèm {@code reason}.
	 */
	@Transactional(readOnly = true)
	public Applied check(String code, long subtotal) {
		Voucher voucher = vouchers.findByCode(code.strip().toUpperCase(Locale.ROOT))
			.orElseThrow(VoucherRejection.NOT_FOUND::toException);
		voucher.rejection(subtotal, Instant.now()).ifPresent(rejection -> {
			throw rejection.toException();
		});
		return new Applied(voucher.getId(), voucher.getCode(), voucher.discountFor(subtotal));
	}

	/**
	 * Q-14 (BR-32). Hết lượt thì báo EXHAUSTED. Không kiểm tra lại {@code active} và thời hạn: {@link #check} đã làm
	 * trong cùng transaction, và thay đổi của Admin chỉ áp dụng cho booking mới (FR-100).
	 */
	@Transactional
	public void reserve(long voucherId) {
		int updated = jdbc.sql("""
				UPDATE vouchers
				SET used_count = used_count + 1
				WHERE id = :id AND (usage_limit IS NULL OR used_count < usage_limit)
				""").param("id", voucherId).update();
		if (updated == 0) {
			throw VoucherRejection.EXHAUSTED.toException();
		}
	}

	/** Q-15. Mỗi lượt rời trạng thái giữ thì gọi đúng một lần (APP_FLOW §4.3); không bao giờ xuống dưới 0. */
	@Transactional
	public void release(long voucherId) {
		jdbc.sql("UPDATE vouchers SET used_count = used_count - 1 WHERE id = :id AND used_count > 0")
			.param("id", voucherId)
			.update();
	}

}
