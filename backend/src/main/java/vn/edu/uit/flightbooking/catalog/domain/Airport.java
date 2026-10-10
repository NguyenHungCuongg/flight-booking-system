package vn.edu.uit.flightbooking.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** Bảng {@code airports}. Khoá chính là mã IATA nên không đổi được sau khi tạo (BR-90). */
@Entity
@Table(name = "airports")
@Getter
@Setter
public class Airport {

	@Id
	private String code;

	private String name;

	private String city;

	private String countryCode;

	/** Tên múi giờ IANA, VD {@code Asia/Ho_Chi_Minh} (TDD §7). */
	private String timezone;

	private boolean active = true;

}
