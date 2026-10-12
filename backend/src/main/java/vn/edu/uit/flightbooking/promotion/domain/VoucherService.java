package vn.edu.uit.flightbooking.promotion.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.promotion.infra.VoucherRepository;

/**
 * Quản trị voucher (FR-100). Sửa voucher không ảnh hưởng booking đã tạo vì booking lưu mức giảm đã tính, chỉ giữ
 * {@code voucher_id} để tra lại.
 */
@Service
public class VoucherService {

	private final VoucherRepository vouchers;

	VoucherService(VoucherRepository vouchers) {
		this.vouchers = vouchers;
	}

	/** Màn hình A-07: mọi voucher, mới tạo xếp trước, kèm số lượt đã dùng. */
	@Transactional(readOnly = true)
	public List<Voucher> list() {
		return vouchers.findAllByOrderByIdDesc();
	}

	@Transactional
	public Voucher create(VoucherFields fields) {
		if (vouchers.existsByCode(fields.code())) {
			throw BusinessException.invalidField("code", "Mã voucher đã tồn tại");
		}
		Voucher voucher = new Voucher();
		voucher.setCode(fields.code());
		apply(voucher, fields);
		return vouchers.save(voucher);
	}

	/** Khoá dòng trước khi so {@code usageLimit} với {@code usedCount} để lượt vừa giữ ở request khác không bị bỏ sót. */
	@Transactional
	public Voucher update(long id, VoucherFields fields) {
		Voucher voucher = vouchers.findForUpdate(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy voucher"));
		if (!voucher.getCode().equals(fields.code())) {
			throw BusinessException.invalidField("code", "Không đổi được mã voucher đã tạo");
		}
		if (fields.usageLimit() != null && fields.usageLimit() < voucher.getUsedCount()) {
			throw BusinessException.invalidField("usageLimit",
					"Không được nhỏ hơn số lượt đã dùng (" + voucher.getUsedCount() + ")");
		}
		apply(voucher, fields);
		return voucher;
	}

	/** Các ràng buộc CHECK của bảng {@code vouchers} mà Bean Validation không bắt được, báo theo trường. */
	private static void apply(Voucher voucher, VoucherFields fields) {
		if (fields.discountType() == DiscountType.PERCENT && fields.discountValue() > 100) {
			throw BusinessException.invalidField("discountValue", "Phần trăm giảm từ 1 đến 100");
		}
		if (fields.discountType() == DiscountType.FIXED && fields.maxDiscount() != null) {
			throw BusinessException.invalidField("maxDiscount", "Chỉ dùng cho voucher giảm theo phần trăm");
		}
		Instant from = fields.validFrom().toInstant();
		Instant to = fields.validTo().toInstant();
		if (!to.isAfter(from)) {
			throw BusinessException.invalidField("validTo", "Thời điểm kết thúc phải sau thời điểm bắt đầu");
		}
		voucher.setDescription(fields.description());
		voucher.setDiscountType(fields.discountType());
		voucher.setDiscountValue(fields.discountValue());
		voucher.setMaxDiscount(fields.maxDiscount());
		voucher.setMinOrderAmount(fields.minOrderAmount());
		voucher.setValidFrom(from);
		voucher.setValidTo(to);
		voucher.setUsageLimit(fields.usageLimit());
		voucher.setActive(fields.active());
	}

}
