package vn.edu.uit.flightbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/** {@code @EnableAsync}: listener {@code @ApplicationModuleListener} (gửi email) chạy bất đồng bộ (TDD §3.3). */
@SpringBootApplication
@EnableAsync
public class FlightBookingApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlightBookingApplication.class, args);
	}

}
