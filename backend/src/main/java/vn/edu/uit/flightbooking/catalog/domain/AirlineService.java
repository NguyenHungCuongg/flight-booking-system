package vn.edu.uit.flightbooking.catalog.domain;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.infra.AirlineRepository;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** Hãng bay (FR-81, BR-90, BR-91). */
@Service
public class AirlineService {

	private final AirlineRepository airlines;

	AirlineService(AirlineRepository airlines) {
		this.airlines = airlines;
	}

	@Transactional(readOnly = true)
	public List<Airline> listActive() {
		return airlines.findByActiveTrueOrderByCode();
	}

	/** Màn hình A-02: mọi hãng, kể cả đã ngừng kích hoạt. */
	@Transactional(readOnly = true)
	public List<Airline> list() {
		return airlines.findAllByOrderByCode();
	}

	/** Không có hãng thì báo RESOURCE_NOT_FOUND. */
	@Transactional(readOnly = true)
	public Airline get(String code) {
		return airlines.findById(code)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hãng bay"));
	}

	@Transactional
	public Airline create(String code, String name, String ticketPrefix, boolean active) {
		// Khoá chính do người dùng nhập: save() với mã đã có sẽ ghi đè bản cũ thay vì báo lỗi, nên phải kiểm tra trước.
		if (airlines.existsById(code)) {
			throw BusinessException.invalidField("code", "Mã hãng đã tồn tại");
		}
		Airline airline = new Airline();
		airline.setCode(code);
		apply(airline, name, ticketPrefix, active);
		return airlines.save(airline);
	}

	@Transactional
	public Airline update(String code, String name, String ticketPrefix, boolean active) {
		Airline airline = get(code);
		apply(airline, name, ticketPrefix, active);
		return airline;
	}

	/** BR-91: hãng đã có gói giá, mức hành lý hoặc chuyến bay thì khoá ngoại chặn xoá. */
	@Transactional
	public void delete(String code) {
		try {
			airlines.delete(get(code));
			airlines.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.RESOURCE_IN_USE,
					"Hãng đã có gói giá, bảng giá hành lý hoặc chuyến bay nên không xoá được, hãy ngừng kích hoạt");
		}
	}

	/** BR-90: mã số vé là duy nhất giữa các hãng. */
	private void apply(Airline airline, String name, String ticketPrefix, boolean active) {
		airlines.findByTicketPrefix(ticketPrefix)
			.filter(other -> !other.getCode().equals(airline.getCode()))
			.ifPresent(other -> {
				throw BusinessException.invalidField("ticketPrefix", "Mã số vé đã được hãng " + other.getCode() + " dùng");
			});
		airline.setName(name);
		airline.setTicketPrefix(ticketPrefix);
		airline.setActive(active);
	}

}
