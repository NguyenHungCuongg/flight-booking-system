package vn.edu.uit.flightbooking.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** Bảng {@code baggage_options}. Giá tính cho mỗi hành khách, mỗi chiều (BR-22). */
@Entity
@Table(name = "baggage_options")
@Getter
@Setter
public class BaggageOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String airlineCode;

	private int weightKg;

	private long price;

	private boolean active = true;

}
