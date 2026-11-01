package vn.edu.uit.flightbooking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.TestVnpayApi;

/**
 * Mọi integration test dùng chung annotation này để Spring cache một context
 * và chỉ khởi động một container PostgreSQL cho cả bộ test. Email gửi đi được giữ trong {@link TestMailSender}.
 * VNPay dùng mã và secret riêng của test ({@link TestVnpay}), không đọc {@code .env}.
 * Refund API của VNPay được thay bằng {@link TestVnpayApi}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = { "app.vnpay.tmn-code=" + TestVnpay.TMN_CODE,
		"app.vnpay.hash-secret=" + TestVnpay.SECRET })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestMailSender.class, TestVnpayApi.class })
public @interface IntegrationTest {
}
