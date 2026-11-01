package vn.edu.uit.flightbooking.catalog.domain;

import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.infra.BaggageOptionRepository;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** Bảng giá hành lý mua thêm của hãng (FR-83, BR-91, BR-92). */
@Service
public class BaggageOptionService {

	private final BaggageOptionRepository options;

	private final AirlineService airlines;

	BaggageOptionService(BaggageOptionRepository options, AirlineService airlines) {
		this.options = options;
		this.airlines = airlines;
	}

	/**
	 * Mức đang bán của hãng, nhẹ trước (TDD §8.2). Không xét hãng có đang hoạt động không: chuyến đã có của hãng
	 * ngừng kích hoạt vẫn bán bình thường (BR-91).
	 */
	@Transactional(readOnly = true)
	public List<BaggageOption> listActive(String airlineCode) {
		airlines.get(airlineCode);
		return options.findByAirlineCodeAndActiveTrueOrderByWeightKg(airlineCode);
	}

	/** Màn hình A-03: mọi mức của hãng, kể cả đã ngừng bán. */
	@Transactional(readOnly = true)
	public List<BaggageOption> list(String airlineCode) {
		airlines.get(airlineCode);
		return options.findByAirlineCodeOrderByWeightKg(airlineCode);
	}

	@Transactional
	public BaggageOption create(String airlineCode, int weightKg, long price, boolean active) {
		airlines.get(airlineCode);
		BaggageOption option = new BaggageOption();
		option.setAirlineCode(airlineCode);
		apply(option, weightKg, price, active);
		return options.save(option);
	}

	@Transactional
	public BaggageOption update(long id, int weightKg, long price, boolean active) {
		BaggageOption option = get(id);
		apply(option, weightKg, price, active);
		return option;
	}

	/** BR-91: mức đã có người mua thì khoá ngoại chặn xoá. */
	@Transactional
	public void delete(long id) {
		try {
			options.delete(get(id));
			options.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.RESOURCE_IN_USE,
					"Mức hành lý đã có người mua nên không xoá được, hãy ngừng bán");
		}
	}

	private BaggageOption get(long id) {
		return options.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy mức hành lý"));
	}

	/** BR-92: trong một hãng, mức kg không trùng nhau. */
	private void apply(BaggageOption option, int weightKg, long price, boolean active) {
		options.findByAirlineCodeAndWeightKg(option.getAirlineCode(), weightKg)
			.filter(other -> !Objects.equals(other.getId(), option.getId()))
			.ifPresent(other -> {
				throw BusinessException.invalidField("weightKg", "Hãng đã có mức hành lý " + weightKg + " kg");
			});
		option.setWeightKg(weightKg);
		option.setPrice(price);
		option.setActive(active);
	}

}
