package vn.edu.uit.flightbooking.catalog;

import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.domain.Airline;
import vn.edu.uit.flightbooking.catalog.domain.BaggageOption;
import vn.edu.uit.flightbooking.catalog.domain.FareFamily;
import vn.edu.uit.flightbooking.catalog.infra.AirlineRepository;
import vn.edu.uit.flightbooking.catalog.infra.AirportRepository;
import vn.edu.uit.flightbooking.catalog.infra.BaggageOptionRepository;
import vn.edu.uit.flightbooking.catalog.infra.FareFamilyRepository;

/**
 * Những gì module khác cần đọc từ danh mục (TDD §3.2): flight dùng khi tạo và sửa chuyến, booking dùng khi báo giá
 * hành lý.
 */
@Service
public class CatalogApi {

	/** {@code zone} lấy từ tên múi giờ IANA của sân bay (BR-90). */
	public record AirportRef(ZoneId zone, boolean active) {
	}

	/** Một mức hành lý, kể cả đã ngừng bán; giá cho mỗi hành khách, mỗi chiều (BR-22). */
	public record BaggageRef(String airlineCode, int weightKg, long price, boolean active) {
	}

	private final AirportRepository airports;

	private final AirlineRepository airlines;

	private final FareFamilyRepository fareFamilies;

	private final BaggageOptionRepository baggage;

	CatalogApi(AirportRepository airports, AirlineRepository airlines, FareFamilyRepository fareFamilies,
			BaggageOptionRepository baggage) {
		this.airports = airports;
		this.airlines = airlines;
		this.fareFamilies = fareFamilies;
		this.baggage = baggage;
	}

	@Transactional(readOnly = true)
	public Optional<AirportRef> airport(String code) {
		return airports.findById(code).map(a -> new AirportRef(ZoneId.of(a.getTimezone()), a.isActive()));
	}

	/** Hãng không tồn tại cũng trả {@code false}. */
	@Transactional(readOnly = true)
	public boolean airlineActive(String code) {
		return airlines.findById(code).map(Airline::isActive).orElse(false);
	}

	/** Mọi gói giá của hãng, kể cả đã ngừng bán: id gói → hạng ghế. */
	@Transactional(readOnly = true)
	public Map<Long, CabinClass> fareFamilyCabins(String airlineCode) {
		return fareFamilies.findByAirlineCodeOrderById(airlineCode)
			.stream()
			.collect(Collectors.toMap(FareFamily::getId, FareFamily::getCabinClass));
	}

	/** Mức hành lý theo id; id không tồn tại thì không có trong kết quả. */
	@Transactional(readOnly = true)
	public Map<Long, BaggageRef> baggageOptions(Collection<Long> ids) {
		return baggage.findAllById(ids)
			.stream()
			.collect(Collectors.toMap(BaggageOption::getId,
					b -> new BaggageRef(b.getAirlineCode(), b.getWeightKg(), b.getPrice(), b.isActive())));
	}

}
