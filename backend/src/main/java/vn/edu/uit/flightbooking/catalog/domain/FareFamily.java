package vn.edu.uit.flightbooking.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import vn.edu.uit.flightbooking.catalog.CabinClass;

/** Bảng {@code fare_families}. Phí hoàn và phí đổi tính cho mỗi khách chiếm ghế, mỗi chiều. */
@Entity
@Table(name = "fare_families")
@Getter
@Setter
public class FareFamily {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String airlineCode;

	@Enumerated(EnumType.STRING)
	private CabinClass cabinClass;

	private String name;

	private int carryOnKg;

	private int checkedBaggageKg;

	private boolean refundable;

	private long refundFee;

	private boolean changeable;

	private long changeFee;

	private boolean active = true;

}
