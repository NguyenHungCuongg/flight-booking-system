package vn.edu.uit.flightbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableAsync}: listener {@code @ApplicationModuleListener} (gửi email) chạy bất đồng bộ (TDD §3.3). {@code @EnableScheduling}: job hết hạn giữ chỗ (TDD §6.5). */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class FlightBookingApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlightBookingApplication.class, args);
	}

}
