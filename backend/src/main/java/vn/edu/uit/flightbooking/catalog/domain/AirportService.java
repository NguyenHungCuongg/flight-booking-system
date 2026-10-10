package vn.edu.uit.flightbooking.catalog.domain;

import java.text.Normalizer;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.infra.AirportRepository;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** Sân bay: gợi ý công khai (FR-10) và quản trị (FR-80, BR-90, BR-91). */
@Service
public class AirportService {

	private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

	private final AirportRepository airports;

	AirportService(AirportRepository airports) {
		this.airports = airports;
	}

	/**
	 * FR-10: sân bay đang hoạt động có mã, tên hoặc thành phố chứa {@code query}, không phân biệt hoa thường và dấu
	 * ("ha noi" khớp "Hà Nội"). Sân bay đúng mã đứng đầu, còn lại theo mã.
	 */
	@Transactional(readOnly = true)
	public List<Airport> suggest(String query) {
		String q = fold(query);
		// ponytail: lọc trong bộ nhớ vì bảng chỉ vài chục dòng; lên hàng nghìn dòng thì chuyển sang unaccent của PostgreSQL.
		return airports.findByActiveTrueOrderByCode().stream()
			.filter(a -> fold(a.getCode() + " " + a.getName() + " " + a.getCity()).contains(q))
			.sorted(Comparator.comparing(a -> !fold(a.getCode()).equals(q)))
			.toList();
	}

	/** Màn hình A-01: mọi sân bay, kể cả đã ngừng kích hoạt. */
	@Transactional(readOnly = true)
	public List<Airport> list() {
		return airports.findAllByOrderByCode();
	}

	@Transactional
	public Airport create(String code, String name, String city, String countryCode, String timezone, boolean active) {
		// Khoá chính do người dùng nhập: save() với mã đã có sẽ ghi đè bản cũ thay vì báo lỗi, nên phải kiểm tra trước.
		if (airports.existsById(code)) {
			throw BusinessException.invalidField("code", "Mã sân bay đã tồn tại");
		}
		Airport airport = new Airport();
		airport.setCode(code);
		apply(airport, name, city, countryCode, timezone, active);
		return airports.save(airport);
	}

	@Transactional
	public Airport update(String code, String name, String city, String countryCode, String timezone, boolean active) {
		Airport airport = get(code);
		apply(airport, name, city, countryCode, timezone, active);
		return airport;
	}

	/** BR-91: sân bay đã có chuyến bay thì khoá ngoại chặn xoá, chỉ ngừng kích hoạt được. */
	@Transactional
	public void delete(String code) {
		try {
			airports.delete(get(code));
			airports.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.RESOURCE_IN_USE,
					"Sân bay đã có chuyến bay nên không xoá được, hãy ngừng kích hoạt");
		}
	}

	private Airport get(String code) {
		return airports.findById(code)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sân bay"));
	}

	/** BR-90: quốc gia theo ISO 3166-1 alpha-2, múi giờ là tên IANA (từ chối dạng offset như "GMT+7"). */
	private static void apply(Airport airport, String name, String city, String countryCode, String timezone,
			boolean active) {
		if (!COUNTRIES.contains(countryCode)) {
			throw BusinessException.invalidField("countryCode", "Mã quốc gia không có trong ISO 3166-1, VD VN");
		}
		if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
			throw BusinessException.invalidField("timezone", "Múi giờ phải là tên IANA, VD Asia/Ho_Chi_Minh");
		}
		airport.setName(name);
		airport.setCity(city);
		airport.setCountryCode(countryCode);
		airport.setTimezone(timezone);
		airport.setActive(active);
	}

	/** Bỏ dấu tiếng Việt (cả "đ") và đổi sang chữ thường. */
	private static String fold(String s) {
		return Normalizer.normalize(s.strip(), Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.replace('đ', 'd')
			.replace('Đ', 'D')
			.toLowerCase(Locale.ROOT);
	}

}
