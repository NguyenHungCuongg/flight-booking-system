package vn.edu.uit.flightbooking.aftersales.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.aftersales.domain.RefundQueries;
import vn.edu.uit.flightbooking.aftersales.domain.RefundQueries.RefundDetail;
import vn.edu.uit.flightbooking.aftersales.domain.RefundQueries.RefundSummary;
import vn.edu.uit.flightbooking.aftersales.domain.RefundRequests;
import vn.edu.uit.flightbooking.aftersales.domain.RefundSource;
import vn.edu.uit.flightbooking.aftersales.domain.RefundStatus;
import vn.edu.uit.flightbooking.aftersales.domain.StaffRefunds;
import vn.edu.uit.flightbooking.common.CurrentUser;

/**
 * Màn hình S-03, S-04: hàng đợi yêu cầu hoàn, duyệt, từ chối, thử lại, xác nhận hoàn thủ công (FR-71). Mỗi thao tác
 * trả chi tiết mới nhất. Quyền STAFF (Admin kế thừa) theo đường dẫn ở SecurityConfig.
 */
@RestController
@RequestMapping("/api/staff/refund-requests")
class StaffRefundController {

	private final RefundQueries queries;

	private final RefundRequests requests;

	private final StaffRefunds staffRefunds;

	StaffRefundController(RefundQueries queries, RefundRequests requests, StaffRefunds staffRefunds) {
		this.queries = queries;
		this.requests = requests;
		this.staffRefunds = staffRefunds;
	}

	@GetMapping
	PagedModel<RefundSummary> page(@RequestParam(required = false) RefundStatus status,
			@RequestParam(required = false) RefundSource source, @PageableDefault(size = 20) Pageable pageable) {
		return new PagedModel<>(queries.page(status, source, pageable));
	}

	@GetMapping("/{id}")
	RefundDetail detail(@PathVariable long id) {
		return queries.detail(id);
	}

	@PostMapping("/{id}/approve")
	RefundDetail approve(@AuthenticationPrincipal CurrentUser me, @PathVariable long id,
			@RequestBody(required = false) @Valid NoteBody body) {
		staffRefunds.approve(id, me.id(), note(body));
		return queries.detail(id);
	}

	@PostMapping("/{id}/reject")
	RefundDetail reject(@AuthenticationPrincipal CurrentUser me, @PathVariable long id,
			@Valid @RequestBody ReasonBody body) {
		requests.reject(id, me.id(), body.reason());
		return queries.detail(id);
	}

	@PostMapping("/{id}/retry")
	RefundDetail retry(@AuthenticationPrincipal CurrentUser me, @PathVariable long id) {
		staffRefunds.retry(id, me.id());
		return queries.detail(id);
	}

	@PostMapping("/{id}/mark-manual")
	RefundDetail markManual(@AuthenticationPrincipal CurrentUser me, @PathVariable long id,
			@RequestBody(required = false) @Valid NoteBody body) {
		requests.markManual(id, me.id(), note(body));
		return queries.detail(id);
	}

	private static String note(NoteBody body) {
		return body == null ? null : body.note();
	}

}
