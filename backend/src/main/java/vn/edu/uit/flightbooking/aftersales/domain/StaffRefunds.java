package vn.edu.uit.flightbooking.aftersales.domain;

import org.springframework.stereotype.Service;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * Duyệt và thử lại (TDD §6.6): bước 1 và bước 2 là hai transaction riêng. Lớp này cố ý KHÔNG {@code @Transactional}:
 * lỗi cổng thanh toán chỉ được ném sau khi bước 2 đã commit dòng hoàn và {@code last_error} (BR-64).
 */
@Service
public class StaffRefunds {

	private final RefundRequests requests;

	StaffRefunds(RefundRequests requests) {
		this.requests = requests;
	}

	public void approve(long id, long staffId, String note) {
		requests.approve(id, staffId, note);
		finish(id, staffId);
	}

	public void retry(long id, long staffId) {
		finish(id, staffId);
	}

	private void finish(long id, long staffId) {
		requests.execute(id, staffId).ifPresent(error -> {
			throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR, "Hoàn tiền qua VNPay chưa xong: " + error);
		});
	}

}
