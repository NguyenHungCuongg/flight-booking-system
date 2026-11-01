package vn.edu.uit.flightbooking.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** Bảng {@code airlines}. Khoá chính là mã IATA nên không đổi được sau khi tạo (BR-90). */
@Entity
@Table(name = "airlines")
@Getter
@Setter
public class Airline {

	@Id
	private String code;

	private String name;

	/** 3 chữ số đầu của số vé điện tử (BR-43). */
	private String ticketPrefix;

	private boolean active = true;

}
