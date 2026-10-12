package vn.edu.uit.flightbooking.promotion.domain;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import vn.edu.uit.flightbooking.promotion.VoucherRejection;

/** Bảng {@code vouchers}. Mã in hoa và không đổi sau khi tạo (FR-100). */
@Entity
@Table(name = "vouchers")
@Getter
@Setter
public class Voucher {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String code;

	private String description;

	@Enumerated(EnumType.STRING)
	private DiscountType discountType;

	/** Phần trăm (1–100) hoặc số đồng, tuỳ {@link #discountType}. */
	private long discountValue;

	/** Chỉ dùng cho PERCENT; {@code null} là không giới hạn. */
	private Long maxDiscount;

	private long minOrderAmount;

	private Instant validFrom;

	private Instant validTo;

	/** {@code null} là không giới hạn lượt. */
	private Integer usageLimit;

	/** Chỉ đổi bằng Q-14, Q-15; JPA không bao giờ ghi cột này để khỏi ghi đè lượt vừa giữ (TDD §5.4). */
	@Column(updatable = false)
	private int usedCount;

	private boolean active = true;

	/**
	 * BR-30 điều kiện 1–4, theo thứ tự liệt kê ở TDD §9. Điều kiện 5 (mỗi tài khoản một lần) do booking kiểm tra.
	 * Rỗng nghĩa là dùng được.
	 */
	public Optional<VoucherRejection> rejection(long subtotal, Instant now) {
		if (!active) {
			return Optional.of(VoucherRejection.INACTIVE);
		}
		if (now.isBefore(validFrom)) {
			return Optional.of(VoucherRejection.NOT_STARTED);
		}
		if (!now.isBefore(validTo)) {
			return Optional.of(VoucherRejection.EXPIRED);
		}
		if (usageLimit != null && usedCount >= usageLimit) {
			return Optional.of(VoucherRejection.EXHAUSTED);
		}
		if (subtotal < minOrderAmount) {
			return Optional.of(VoucherRejection.MIN_ORDER_NOT_MET);
		}
		return Optional.empty();
	}

	/** BR-31: phần trăm làm tròn xuống (chia nguyên, TDD §5.2), không vượt mức tối đa và không vượt tạm tính. */
	public long discountFor(long subtotal) {
		long discount = discountValue;
		if (discountType == DiscountType.PERCENT) {
			discount = subtotal * discountValue / 100;
			if (maxDiscount != null) {
				discount = Math.min(discount, maxDiscount);
			}
		}
		return Math.min(discount, subtotal);
	}

}
