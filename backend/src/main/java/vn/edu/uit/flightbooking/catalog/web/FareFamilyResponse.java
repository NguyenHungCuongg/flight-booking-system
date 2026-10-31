package vn.edu.uit.flightbooking.catalog.web;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.catalog.domain.FareFamily;

/** Gói giá trả về cho màn hình A-03 và cho khách (điều kiện hành lý, hoàn, đổi). */
record FareFamilyResponse(long id, String airlineCode, CabinClass cabinClass, String name, int carryOnKg,
		int checkedBaggageKg, boolean refundable, long refundFee, boolean changeable, long changeFee,
		boolean active) {

	static FareFamilyResponse of(FareFamily f) {
		return new FareFamilyResponse(f.getId(), f.getAirlineCode(), f.getCabinClass(), f.getName(),
				f.getCarryOnKg(), f.getCheckedBaggageKg(), f.isRefundable(), f.getRefundFee(), f.isChangeable(),
				f.getChangeFee(), f.isActive());
	}

}
