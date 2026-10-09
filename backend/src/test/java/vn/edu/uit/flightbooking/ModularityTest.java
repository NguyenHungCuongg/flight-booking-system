package vn.edu.uit.flightbooking;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Chặn phụ thuộc vòng và truy cập gói nội bộ của module khác (TDD §3.2). */
class ModularityTest {

	@Test
	void modulesRespectBoundaries() {
		ApplicationModules.of(FlightBookingApplication.class).verify();
	}

}
