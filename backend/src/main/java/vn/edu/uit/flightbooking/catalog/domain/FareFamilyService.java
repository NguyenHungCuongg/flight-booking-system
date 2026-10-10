package vn.edu.uit.flightbooking.catalog.domain;

import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.infra.FareFamilyRepository;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** Gói giá của hãng (FR-82, BR-91, BR-92). Sửa gói không ảnh hưởng vé đã bán vì booking lưu bản sao (BR-24). */
@Service
public class FareFamilyService {

	private final FareFamilyRepository fareFamilies;

	private final AirlineService airlines;

	FareFamilyService(FareFamilyRepository fareFamilies, AirlineService airlines) {
		this.fareFamilies = fareFamilies;
		this.airlines = airlines;
	}

	/** Màn hình A-03: mọi gói của hãng, kể cả đã ngừng bán. */
	@Transactional(readOnly = true)
	public List<FareFamily> list(String airlineCode) {
		airlines.get(airlineCode);
		return fareFamilies.findByAirlineCodeOrderById(airlineCode);
	}

	@Transactional
	public FareFamily create(String airlineCode, FareFamilyFields fields) {
		airlines.get(airlineCode);
		FareFamily fareFamily = new FareFamily();
		fareFamily.setAirlineCode(airlineCode);
		fareFamily.setCabinClass(fields.cabinClass());
		apply(fareFamily, fields);
		return fareFamilies.save(fareFamily);
	}

	/** Không đổi được hạng ghế: giá bán của các chuyến đã đặt theo gói giá trong đúng hạng ghế đó. */
	@Transactional
	public FareFamily update(long id, FareFamilyFields fields) {
		FareFamily fareFamily = get(id);
		if (fields.cabinClass() != fareFamily.getCabinClass()) {
			throw BusinessException.invalidField("cabinClass", "Không đổi được hạng ghế của gói giá đã tạo");
		}
		apply(fareFamily, fields);
		return fareFamily;
	}

	/** BR-91: gói đã có giá bán trên chuyến hoặc đã nằm trong booking thì khoá ngoại chặn xoá. */
	@Transactional
	public void delete(long id) {
		try {
			fareFamilies.delete(get(id));
			fareFamilies.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.RESOURCE_IN_USE,
					"Gói giá đã được bán trên chuyến bay nên không xoá được, hãy ngừng bán");
		}
	}

	private FareFamily get(long id) {
		return fareFamilies.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy gói giá"));
	}

	/** BR-92: trong một hãng, tên gói không trùng nhau. */
	private void apply(FareFamily fareFamily, FareFamilyFields fields) {
		fareFamilies.findByAirlineCodeAndName(fareFamily.getAirlineCode(), fields.name())
			.filter(other -> !Objects.equals(other.getId(), fareFamily.getId()))
			.ifPresent(other -> {
				throw BusinessException.invalidField("name", "Hãng đã có gói giá tên này");
			});
		fareFamily.setName(fields.name());
		fareFamily.setCarryOnKg(fields.carryOnKg());
		fareFamily.setCheckedBaggageKg(fields.checkedBaggageKg());
		fareFamily.setRefundable(fields.refundable());
		fareFamily.setRefundFee(fields.refundFee());
		fareFamily.setChangeable(fields.changeable());
		fareFamily.setChangeFee(fields.changeFee());
		fareFamily.setActive(fields.active());
	}

}
