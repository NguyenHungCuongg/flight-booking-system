package vn.edu.uit.flightbooking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Mọi integration test dùng chung annotation này để Spring cache một context
 * và chỉ khởi động một container PostgreSQL cho cả bộ test. Email gửi đi được giữ trong {@link TestMailSender}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestMailSender.class })
public @interface IntegrationTest {
}
