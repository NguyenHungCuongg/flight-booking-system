# Plan 01 — Nền móng backend: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dựng backend Spring Boot chạy trên PostgreSQL 18 với đủ schema V1, định dạng lỗi ProblemDetail, lớp bảo mật nền, API đọc tham số hệ thống, kiểm tra ranh giới module, Docker Compose và CI. Đây là nền cho cả 20 plan sau.

**Architecture:** Modular monolith kiểm soát bằng Spring Modulith. Plan này chỉ tạo module `common` (module mở), gồm mã lỗi, xử lý lỗi, cấu hình bảo mật và đọc tham số. Các module nghiệp vụ được thêm ở các plan sau. Mọi integration test chạy trên PostgreSQL 18 thật qua Testcontainers và dùng chung một Spring context.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7, Hibernate 7.4), Spring Modulith 2.1.1, Flyway, springdoc-openapi 3.1.1, PostgreSQL 18, Testcontainers 2, JUnit Jupiter, MockMvc, Docker Compose, GitHub Actions.

**Spec:** [TDD](../../TDD.md) §2, §3, §4.2–4.3, §5.3, §6.9, §9, §10, §11 · [Backend Schema](../../BACKEND_SCHEMA.md) §5 · [PRD](../../PRD.md) §7, NFR-03, NFR-04, NFR-07, NFR-08, NFR-10 · [Lộ trình](2026-10-09-00-roadmap.md)

## Global Constraints

- Java 21 (Maven biên dịch với `--release 21`; máy dev dùng JDK 21 trở lên đều được). Spring Boot `4.1.1`, Spring Modulith `2.1.1`, springdoc-openapi `3.1.1`, image `postgres:18`.
- Gói gốc `vn.edu.uit.flightbooking`, lớp khởi chạy `FlightBookingApplication`, groupId `vn.edu.uit`, artifactId `flightbooking`.
- DDL ở BACKEND_SCHEMA §5 được dùng **nguyên văn** làm `V1__init.sql`. Hibernate để `ddl-auto=validate`.
- Lỗi theo `ProblemDetail` (RFC 9457) và có thêm trường `code` lấy từ bảng TDD §9. Lỗi validation có thêm mảng `errors` gồm các phần tử `{ field, message }`.
- Phân quyền theo đường dẫn đúng bảng TDD §4.3. `ROLE_ADMIN > ROLE_STAFF`.
- CSRF: cookie `XSRF-TOKEN` (JavaScript đọc được); mọi request `POST`, `PUT`, `PATCH`, `DELETE` gửi lại giá trị cookie trong header `X-XSRF-TOKEN`.
- Cookie phiên `HttpOnly`, `SameSite=Lax`, thêm `Secure` khi chạy HTTPS; phiên hết hạn sau 30 phút không hoạt động.
- Bí mật chỉ lấy từ biến môi trường hoặc `.env` (không commit). Swagger và `/v3/api-docs` chỉ bật ở profile `dev`.
- Câu thông báo lỗi trả cho người dùng viết bằng tiếng Việt (NFR-09).
- Bốn điểm cố ý khác TDD (lý do ở [Lộ trình §7](2026-10-09-00-roadmap.md#7-quyết-định-cần-chốt)): `SettingsApi` không cache; `GET /api/auth/csrf` trả 204; lỗi 4xx khác của Spring MVC dùng `VALIDATION_FAILED`; cổng PostgreSQL trên host đổi được qua `DB_PORT`.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng đã có test ghim lại.

1. **API ghi công khai vẫn cần CSRF.** Khách chưa đăng nhập gọi `POST /api/auth/login` mà không có token phải nhận 403 `FORBIDDEN` dạng JSON, không lọt qua (Task 2, `publicWriteEndpointsStillNeedCsrfToken`). Frontend vì vậy phải gọi `/api/auth/csrf` trước khi đăng nhập.
2. **Tham số sai kiểu** (`?page=abc`) phải trả 400 `VALIDATION_FAILED`, không phải 500 (Task 3, `wrongParameterTypeIsValidationFailed`).
3. **Dấu `/` ở cuối URL** (`/api/admin/settings/`) không được vượt qua kiểm tra vai trò (Task 2, `trailingSlashDoesNotBypassRoleCheck`).
4. **Sai HTTP method** vẫn trả ProblemDetail có `code`, để frontend xử lý lỗi theo một mẫu duy nhất (Task 3, `wrongHttpMethodStillHasCode`).
5. **Swagger không lộ ra ngoài profile `dev`**: `/v3/api-docs` trả 404 khi chạy profile mặc định (Task 2, `apiDocsAreHiddenOutsideDevProfile`).

---

## Trước khi bắt đầu

- **Cần có:** Docker Desktop đang chạy (Testcontainers cần Docker), Git Bash, JDK 21 trở lên, `curl`, `unzip`.
- **Chạy lệnh ở đâu:** mọi lệnh dùng Git Bash và chạy từ thư mục gốc repo, trừ khi bước đó ghi `cd backend`.
- **Nhánh:** làm trên một nhánh riêng, VD `feat/01-backend-foundation`.
- **Đã kiểm chứng:** toàn bộ code và lệnh trong plan đã chạy thử ngày 2026-10-09 trên Windows 11 với JDK 25, Docker 29. Kết quả: 26 test xanh, `docker compose up` cả 3 service healthy, `actionlint` không báo lỗi.
- **Lần đầu chạy `./mvnw`** sẽ tải Maven và dependency, mất vài phút.
- **Cảnh báo vô hại trong log:**
  - `UserDetailsServiceAutoConfiguration` in ra một mật khẩu sinh tự động. Plan 02 thêm `UserDetailsService` thật và cảnh báo này biến mất.
  - `Cannot find template location: classpath:/templates/`. Plan 02 thêm template email.
  - `sun.misc.Unsafe ... lombok` (chỉ xuất hiện khi chạy JDK 25 trở lên).

## Cấu trúc file sau plan

```
/
├── .env.example                          # Task 6: hợp đồng biến môi trường
├── docker-compose.yml                    # Task 6: postgres, mailpit, backend
├── .github/workflows/ci.yml              # Task 5: job backend
├── CLAUDE.md                             # Task 6: thêm lệnh build/test
└── backend/
    ├── Dockerfile, .dockerignore         # Task 6
    ├── pom.xml, mvnw, mvnw.cmd, .mvn/    # Task 1 (Spring Initializr)
    └── src/
        ├── main/java/vn/edu/uit/flightbooking/
        │   ├── FlightBookingApplication.java
        │   └── common/
        │       ├── package-info.java              # Task 5: module mở
        │       ├── ErrorCode.java                 # Task 2
        │       ├── BusinessException.java         # Task 2
        │       ├── SettingKey.java                # Task 4
        │       ├── SettingsApi.java               # Task 4
        │       └── web/
        │           ├── GlobalExceptionHandler.java  # Task 2, hoàn thiện ở Task 3
        │           ├── SecurityConfig.java          # Task 2
        │           └── CsrfController.java          # Task 2
        ├── main/resources/
        │   ├── application.properties, application-dev.properties  # Task 1
        │   └── db/migration/V1__init.sql                            # Task 1
        └── test/java/vn/edu/uit/flightbooking/
            ├── TestcontainersConfiguration.java   # Task 1
            ├── IntegrationTest.java               # Task 1
            ├── TestCsrf.java                      # Task 2
            ├── ModularityTest.java                # Task 5
            └── common/
                ├── MigrationTest.java             # Task 1
                ├── SettingsApiTest.java           # Task 4
                └── web/
                    ├── SecurityConfigTest.java    # Task 2
                    ├── TestProbeController.java   # Task 3
                    └── ErrorHandlingTest.java     # Task 3
```

---

### Task 1: Khởi tạo backend và schema V1

**Files:**
- Create: `backend/` (sinh bằng Spring Initializr)
- Modify: `backend/pom.xml`, `backend/src/main/resources/application.properties`, `backend/src/test/java/vn/edu/uit/flightbooking/TestcontainersConfiguration.java`
- Create: `backend/src/main/resources/application-dev.properties`, `backend/src/main/resources/db/migration/V1__init.sql`, `backend/src/test/java/vn/edu/uit/flightbooking/IntegrationTest.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/common/MigrationTest.java`
- Delete: `backend/HELP.md`, `backend/src/test/java/vn/edu/uit/flightbooking/TestFlightBookingApplication.java`, `backend/src/test/java/vn/edu/uit/flightbooking/FlightBookingApplicationTests.java`

**Interfaces:**
- Consumes: —
- Produces:
  - `@IntegrationTest`: annotation cho integration test, gồm `@SpringBootTest`, `@AutoConfigureMockMvc` và PostgreSQL 18 qua `TestcontainersConfiguration`.
  - Schema V1: 20 bảng, 8 dòng `system_settings`.

- [ ] **Step 1: Sinh project bằng Spring Initializr**

Chạy từ thư mục gốc repo:

```bash
curl -s https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d bootVersion=4.1.1 -d javaVersion=21 \
  -d baseDir=backend -d groupId=vn.edu.uit -d artifactId=flightbooking \
  -d name=FlightBooking -d packageName=vn.edu.uit.flightbooking \
  -d dependencies=web,data-jpa,security,validation,flyway,postgresql,mail,thymeleaf,actuator,modulith,lombok,testcontainers \
  -o backend.zip && unzip -q backend.zip && rm backend.zip && ls backend
```

Expected: `HELP.md  mvnw  mvnw.cmd  pom.xml  src`. Thư mục `backend/` còn có `.gitattributes` (giữ `mvnw` ở dạng LF), `.gitignore` (bỏ qua `target/`) và `.mvn/`.

Initializr chỉ giữ bản vá mới nhất của mỗi dòng Spring Boot. Nếu lệnh trả lỗi `bootVersion`, xem bản `4.1.x` hiện có bằng `curl -s https://start.spring.io/metadata/client | grep -o '"4\.1\.[0-9]*"' | head -1`, dùng bản đó cho lệnh trên và cho `<version>` ở Step 3.

- [ ] **Step 2: Xoá file thừa**

```bash
rm backend/HELP.md \
   backend/src/test/java/vn/edu/uit/flightbooking/TestFlightBookingApplication.java \
   backend/src/test/java/vn/edu/uit/flightbooking/FlightBookingApplicationTests.java
```

`MigrationTest` ở Step 5 thay cho test `contextLoads`. Dev chạy PostgreSQL bằng Docker Compose, nên không cần `TestFlightBookingApplication`.

- [ ] **Step 3: Thay toàn bộ `backend/pom.xml`**

So với bản Initializr sinh ra, bản này bỏ các dependency observability, actuator của Modulith, Event Publication Registry dùng JPA (TDD §6.10 để ngoài phạm vi) và `thymeleaf-extras-springsecurity6`. Bản này thêm springdoc. Mail và Thymeleaf được giữ ngay từ đầu để các plan sau không phải sửa pom.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.1</version>
		<relativePath/>
	</parent>
	<groupId>vn.edu.uit</groupId>
	<artifactId>flightbooking</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>FlightBooking</name>
	<description>SkyLine flight booking backend</description>
	<properties>
		<java.version>21</java.version>
		<spring-modulith.version>2.1.1</spring-modulith.version>
		<springdoc.version>3.1.1</springdoc.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-mail</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-thymeleaf</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.modulith</groupId>
			<artifactId>spring-modulith-starter-core</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>${springdoc.version}</version>
		</dependency>
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-testcontainers</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.modulith</groupId>
			<artifactId>spring-modulith-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-junit-jupiter</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-postgresql</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.modulith</groupId>
				<artifactId>spring-modulith-bom</artifactId>
				<version>${spring-modulith.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<configuration>
					<annotationProcessorPaths>
						<path>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
						</path>
					</annotationProcessorPaths>
				</configuration>
			</plugin>
		</plugins>
	</build>

</project>
```

- [ ] **Step 4: Viết cấu hình ứng dụng**

Thay toàn bộ `backend/src/main/resources/application.properties`:

```properties
spring.application.name=flightbooking

spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

server.servlet.session.timeout=30m
server.servlet.session.cookie.same-site=lax
server.servlet.session.cookie.secure=${COOKIE_SECURE:false}

springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false
```

Tạo `backend/src/main/resources/application-dev.properties`:

```properties
# Chỉ profile dev đọc ../.env (khi chạy ./mvnw spring-boot:run trong backend/).
# Test không bật profile dev nên không phụ thuộc .env của từng máy.
spring.config.import=optional:file:../.env[.properties]
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true
```

Khi chạy test, `${DB_URL}` không được giải nhưng không gây lỗi, vì Testcontainers (`@ServiceConnection`) cung cấp kết nối DB.

- [ ] **Step 5: Viết hạ tầng test**

Thay toàn bộ `backend/src/test/java/vn/edu/uit/flightbooking/TestcontainersConfiguration.java`. So với bản sinh ra, chỉ khác là ghim image `postgres:18` thay cho `postgres:latest`:

```java
package vn.edu.uit.flightbooking;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

}
```

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/IntegrationTest.java`:

```java
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
 * và chỉ khởi động một container PostgreSQL cho cả bộ test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
```

Spring Boot 4 chuyển `AutoConfigureMockMvc` sang gói `org.springframework.boot.webmvc.test.autoconfigure`; các ví dụ cho Boot 3 dùng gói cũ.

- [ ] **Step 6: Viết test cho schema (chưa có migration)**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/common/MigrationTest.java`:

```java
package vn.edu.uit.flightbooking.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;

@IntegrationTest
class MigrationTest {

	@Autowired
	JdbcClient jdbc;

	@Test
	void createsAllTablesFromSchemaDoc() {
		var tables = jdbc.sql("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
				""").query(String.class).set();

		assertThat(tables).containsExactlyInAnyOrder(
				"users", "password_reset_tokens", "system_settings",
				"airports", "airlines", "fare_families", "baggage_options",
				"flights", "flight_cabins", "flight_fares", "vouchers",
				"bookings", "booking_journeys", "booking_segments", "passengers", "tickets", "booking_baggage",
				"reschedules", "refund_requests", "payments");
	}

	@Test
	void seedsEightSystemSettings() {
		assertThat(jdbc.sql("SELECT count(*) FROM system_settings").query(Long.class).single()).isEqualTo(8);
	}

	@Test
	@Transactional
	void databaseRejectsOverbooking() {
		jdbc.sql("INSERT INTO airports (code, name, city, country_code, timezone) VALUES ('AAA', 'A', 'A', 'VN', 'Asia/Ho_Chi_Minh'), ('BBB', 'B', 'B', 'VN', 'Asia/Ho_Chi_Minh')").update();
		jdbc.sql("INSERT INTO airlines (code, name, ticket_prefix) VALUES ('ZZ', 'Test Air', '999')").update();
		long flightId = jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport, departure_time, arrival_time)
				VALUES ('ZZ', 'ZZ1', 'AAA', 'BBB', now() + interval '1 day', now() + interval '1 day 2 hours')
				RETURNING id
				""").query(Long.class).single();

		assertThatThrownBy(() -> jdbc.sql("INSERT INTO flight_cabins (flight_id, cabin_class, total_seats, available_seats) VALUES (?, 'ECONOMY', 10, 11)")
				.param(flightId).update())
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
```

`@Transactional` ở test cuối giúp dữ liệu được rollback, không lọt sang test khác dùng chung DB.

- [ ] **Step 7: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B clean test -Dtest=MigrationTest
```

Expected: `Tests run: 3, Failures: 1, Errors: 2`. Log có cảnh báo `No migrations found` và lỗi `relation "system_settings" does not exist`.

- [ ] **Step 8: Trích DDL nguyên văn từ BACKEND_SCHEMA.md thành V1**

Chạy từ thư mục gốc repo:

````bash
mkdir -p backend/src/main/resources/db/migration
awk '/^## 5\. DDL/{f=1} f&&/^```sql$/{p=1;next} p&&/^```$/{exit} p' docs/BACKEND_SCHEMA.md \
  > backend/src/main/resources/db/migration/V1__init.sql
wc -l backend/src/main/resources/db/migration/V1__init.sql
head -2 backend/src/main/resources/db/migration/V1__init.sql
tail -1 backend/src/main/resources/db/migration/V1__init.sql
````

Expected:

```
330 backend/src/main/resources/db/migration/V1__init.sql
-- =====================================================================
-- V1__init.sql — Flight Booking System
    ADD CONSTRAINT fk_refund_requests_payment FOREIGN KEY (payment_id) REFERENCES payments (id);
```

Lệnh trích file thay vì chép tay để BACKEND_SCHEMA.md vẫn là nguồn gốc duy nhất. Sau khi commit, V1 không được sửa nữa; mọi thay đổi schema sau này đi vào `V2__...`.

- [ ] **Step 9: Chạy lại test**

```bash
cd backend && ./mvnw -B test -Dtest=MigrationTest
```

Expected: `Tests run: 3, Failures: 0, Errors: 0` và `BUILD SUCCESS`. Log có dòng `Database: jdbc:postgresql://... (PostgreSQL 18.x)` và `Successfully applied 1 migration`.

- [ ] **Step 10: Commit**

Git trên Windows thêm `mvnw` mà không có quyền thực thi. Phải sửa trước khi commit, nếu không CI trên Ubuntu sẽ báo `Permission denied`.

```bash
git add backend
git update-index --chmod=+x backend/mvnw
git ls-files -s backend/mvnw | cut -c1-6
git commit -m "feat(backend): scaffold Spring Boot 4.1 app with V1 schema on PostgreSQL 18"
```

Expected: lệnh `git ls-files` in ra `100755`.

---

### Task 2: Bảo mật nền và lỗi 401/403 dạng ProblemDetail

**Files:**
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/common/ErrorCode.java`, `.../common/BusinessException.java`, `.../common/web/GlobalExceptionHandler.java`, `.../common/web/SecurityConfig.java`, `.../common/web/CsrfController.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/TestCsrf.java`, `backend/src/test/java/vn/edu/uit/flightbooking/common/web/SecurityConfigTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest` (Task 1).
- Produces:
  - `enum ErrorCode`: 24 mã của TDD §9; `HttpStatus status()`.
  - `class BusinessException extends RuntimeException`: hai constructor `(ErrorCode code, String detail)` và `(ErrorCode code, String detail, Map<String, Object> properties)`; hai accessor `ErrorCode code()` và `Map<String, Object> properties()`.
  - `GlobalExceptionHandler`: chuyển `BusinessException` thành `ProblemDetail` có `code` và các trường trong `properties`.
  - `SecurityConfig`: bean `SecurityFilterChain` và `RoleHierarchy`.
  - `GET /api/auth/csrf`: trả 204 và đặt cookie `XSRF-TOKEN`.
  - Test helper `TestCsrf.csrf(MockMvc): RequestPostProcessor`.

- [ ] **Step 1: Viết test helper CSRF và test bảo mật**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/TestCsrf.java`:

```java
package vn.edu.uit.flightbooking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Gửi CSRF token giống trình duyệt thật: lấy cookie XSRF-TOKEN rồi gửi lại trong header X-XSRF-TOKEN.
 * <p>
 * Không dùng {@code SecurityMockMvcRequestPostProcessors.csrf()}: nó thay CsrfTokenRepository
 * của CsrfFilter dùng chung, làm hỏng CSRF của mọi test chạy sau trong cùng Spring context.
 */
public final class TestCsrf {

	private TestCsrf() {
	}

	public static RequestPostProcessor csrf(MockMvc mvc) throws Exception {
		Cookie cookie = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		return request -> {
			request.setCookies(cookie);
			request.addHeader("X-XSRF-TOKEN", cookie.getValue());
			return request;
		};
	}

}
```

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/common/web/SecurityConfigTest.java`:

```java
package vn.edu.uit.flightbooking.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import vn.edu.uit.flightbooking.IntegrationTest;

@IntegrationTest
class SecurityConfigTest {

	@Autowired
	MockMvc mvc;

	/** Request đã qua lớp bảo mật: route chưa tồn tại thì 404, nhưng không được là 401/403. */
	static ResultMatcher passesSecurity() {
		return result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void searchAndCatalogReadsArePublic() throws Exception {
		mvc.perform(get("/api/flights/search")).andExpect(passesSecurity());
		mvc.perform(get("/api/airports")).andExpect(passesSecurity());
		mvc.perform(get("/api/airlines/VN/baggage-options")).andExpect(passesSecurity());
	}

	@Test
	void anonymousUserGetsUnauthenticatedProblem() throws Exception {
		mvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mvc.perform(post("/api/auth/logout").with(csrf(mvc)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "CUSTOMER")
	void customerCannotCallStaffOrAdminApis() throws Exception {
		mvc.perform(get("/api/staff/bookings"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(get("/api/admin/settings")).andExpect(status().isForbidden());
		mvc.perform(get("/api/bookings")).andExpect(passesSecurity());
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void staffCannotBookOrCallAdminApis() throws Exception {
		mvc.perform(get("/api/staff/bookings")).andExpect(passesSecurity());
		mvc.perform(get("/api/bookings")).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/settings")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void adminInheritsStaffRights() throws Exception {
		mvc.perform(get("/api/staff/bookings")).andExpect(passesSecurity());
		mvc.perform(get("/api/admin/settings")).andExpect(passesSecurity());
	}

	@Test
	@WithMockUser(roles = "CUSTOMER")
	void writeRequestsNeedCsrfToken() throws Exception {
		mvc.perform(post("/api/bookings"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(post("/api/bookings").with(csrf(mvc))).andExpect(passesSecurity());
	}

	@Test
	void publicWriteEndpointsStillNeedCsrfToken() throws Exception {
		mvc.perform(post("/api/auth/login"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(post("/api/auth/login").with(csrf(mvc))).andExpect(passesSecurity());
	}

	@Test
	@WithMockUser(roles = "CUSTOMER")
	void trailingSlashDoesNotBypassRoleCheck() throws Exception {
		mvc.perform(get("/api/admin/settings/")).andExpect(status().isForbidden());
	}

	@Test
	void apiDocsAreHiddenOutsideDevProfile() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
	}

	@Test
	void csrfEndpointIssuesCookieReadableByJavaScript() throws Exception {
		mvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andExpect(cookie().httpOnly("XSRF-TOKEN", false));
	}

}
```

Test kiểm tra quyền trên các route chưa có controller (`/api/bookings`, `/api/staff/bookings`…). Với route chưa tồn tại, `passesSecurity()` chỉ đòi status khác 401/403. Khi các plan sau thêm controller thật, test vẫn đúng mà không phải sửa.

- [ ] **Step 2: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=SecurityConfigTest
```

Expected: `Tests run: 11, Failures: 9`. Spring Boot mặc định khoá mọi endpoint và không có trường `code`, nên log có các dòng như `No value at JSON path "$.code"` và `Status expected:<204> but was:<401>`.

- [ ] **Step 3: Viết mã lỗi và exception nghiệp vụ**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/ErrorCode.java`:

```java
package vn.edu.uit.flightbooking.common;

import org.springframework.http.HttpStatus;

/** Mã lỗi trả về trong trường {@code code} của ProblemDetail (TDD §9). */
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
	PASSENGER_RULES_VIOLATED(HttpStatus.BAD_REQUEST),
	ITINERARY_INVALID(HttpStatus.BAD_REQUEST),
	SETTING_OUT_OF_RANGE(HttpStatus.BAD_REQUEST),
	INVALID_TOKEN(HttpStatus.BAD_REQUEST),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
	ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
	EMAIL_ALREADY_USED(HttpStatus.CONFLICT),
	INVALID_STATE(HttpStatus.CONFLICT),
	FLIGHT_NOT_BOOKABLE(HttpStatus.CONFLICT),
	SEATS_UNAVAILABLE(HttpStatus.CONFLICT),
	HOLD_EXPIRED(HttpStatus.CONFLICT),
	VOUCHER_INVALID(HttpStatus.CONFLICT),
	FARE_RULE_NOT_ALLOWED(HttpStatus.CONFLICT),
	DEADLINE_PASSED(HttpStatus.CONFLICT),
	REQUEST_IN_PROGRESS(HttpStatus.CONFLICT),
	FLIGHT_HAS_BOOKINGS(HttpStatus.CONFLICT),
	SEAT_COUNT_BELOW_SOLD(HttpStatus.CONFLICT),
	RESOURCE_IN_USE(HttpStatus.CONFLICT),
	PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/BusinessException.java`:

```java
package vn.edu.uit.flightbooking.common;

import java.util.Map;

/**
 * Lỗi nghiệp vụ. {@code detail} là câu tiếng Việt hiển thị được cho người dùng;
 * {@code properties} là các trường bổ sung của ProblemDetail (VD {@code reason} của VOUCHER_INVALID).
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode code;

	private final Map<String, Object> properties;

	public BusinessException(ErrorCode code, String detail) {
		this(code, detail, Map.of());
	}

	public BusinessException(ErrorCode code, String detail, Map<String, Object> properties) {
		super(detail);
		this.code = code;
		this.properties = properties;
	}

	public ErrorCode code() {
		return code;
	}

	public Map<String, Object> properties() {
		return properties;
	}

}
```

- [ ] **Step 4: Viết bản đầu của bộ xử lý lỗi**

Bản này chỉ xử lý `BusinessException`, đủ cho lỗi 401/403. Task 3 hoàn thiện cho mọi loại lỗi khác.

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/web/GlobalExceptionHandler.java`:

```java
package vn.edu.uit.flightbooking.common.web;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** Chuyển lỗi thành ProblemDetail (RFC 9457) có thêm trường {@code code} (TDD §5.3, §9). */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ProblemDetail handleBusiness(BusinessException ex) {
		ProblemDetail problem = problem(ex.code(), ex.getMessage());
		ex.properties().forEach(problem::setProperty);
		return problem;
	}

	private static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		problem.setProperty("code", code.name());
		return problem;
	}

}
```

- [ ] **Step 5: Viết cấu hình bảo mật và API lấy cookie CSRF**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/web/SecurityConfig.java`:

```java
package vn.edu.uit.flightbooking.common.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** Phân quyền theo đường dẫn (TDD §4.3), CSRF cho SPA (TDD §4.2), lỗi 401/403 dạng ProblemDetail. */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/flights/search", "/api/airports", "/api/airlines/**").permitAll()
				.requestMatchers("/api/auth/logout").authenticated()
				.requestMatchers("/api/auth/**", "/api/payments/vnpay/**").permitAll()
				.requestMatchers("/api/me/**").authenticated()
				.requestMatchers("/api/bookings/**", "/api/reschedules/**").hasRole("CUSTOMER")
				.requestMatchers("/api/staff/**").hasRole("STAFF")
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
				.anyRequest().authenticated())
			.csrf(csrf -> csrf.spa())
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, e) -> resolver.resolveException(request, response, null,
						new BusinessException(ErrorCode.UNAUTHENTICATED, "Bạn chưa đăng nhập hoặc phiên đã hết hạn")))
				.accessDeniedHandler((request, response, e) -> resolver.resolveException(request, response, null,
						new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"))));
		return http.build();
	}

	/** ADMIN có mọi quyền của STAFF. */
	@Bean
	static RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_STAFF");
	}

}
```

Ghi chú:
- **Thứ tự rule quan trọng.** `/api/auth/logout` phải đứng trước `/api/auth/**`, nếu không thì logout sẽ được công khai.
- **401/403 dùng chung bộ xử lý lỗi.** Entry point và access-denied handler chuyển lỗi sang `HandlerExceptionResolver`, nên 401/403 có cùng định dạng ProblemDetail với mọi lỗi khác.
- **`csrf.spa()`** dùng `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN`, JavaScript đọc được, header `X-XSRF-TOKEN`). Đây là cách cấu hình SPA theo tài liệu Spring Security mà TDD §4.2 yêu cầu.

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/web/CsrfController.java`:

```java
package vn.edu.uit.flightbooking.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Frontend gọi API này khi khởi động, sau đăng nhập và sau đăng xuất (TDD §4.2).
 * Với csrf.spa(), CsrfFilter tự đặt cookie XSRF-TOKEN ở mọi request còn thiếu cookie;
 * frontend gửi lại nguyên giá trị cookie trong header X-XSRF-TOKEN.
 */
@RestController
class CsrfController {

	@GetMapping("/api/auth/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void csrf() {
	}

}
```

- [ ] **Step 6: Chạy lại test**

```bash
cd backend && ./mvnw -B test -Dtest=SecurityConfigTest
```

Expected: `Tests run: 11, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add backend/src
git commit -m "feat(common): add path-based security, SPA CSRF and ProblemDetail 401/403"
```

---

### Task 3: ProblemDetail cho mọi loại lỗi

**Files:**
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/common/web/GlobalExceptionHandler.java` (thay toàn bộ)
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/common/web/TestProbeController.java`, `backend/src/test/java/vn/edu/uit/flightbooking/common/web/ErrorHandlingTest.java`

**Interfaces:**
- Consumes: `BusinessException`, `ErrorCode` (Task 2); `TestCsrf.csrf(mvc)` (Task 2).
- Produces: mọi lỗi trả `application/problem+json` dạng `{ type, title, status, detail, instance, code }`, thêm `errors: [{ field, message }]` khi validation lỗi. Lỗi 4xx của Spring MVC (404 thì `RESOURCE_NOT_FOUND`, 4xx khác thì `VALIDATION_FAILED`) và lỗi không lường trước (`INTERNAL_ERROR`, không lộ chi tiết nội bộ) đều có `code`.

- [ ] **Step 1: Viết controller thử và test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/common/web/TestProbeController.java`:

```java
package vn.edu.uit.flightbooking.common.web;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * Endpoint chỉ có trong test để kiểm tra định dạng lỗi. Là lớp top-level trong src/test
 * nên được component scan ở mọi integration test, không tạo thêm Spring context.
 */
@RestController
class TestProbeController {

	@GetMapping("/api/test/business-error")
	void businessError() {
		throw new BusinessException(ErrorCode.SEATS_UNAVAILABLE, "Chuyến VN1825 hạng ECONOMY không còn đủ 3 ghế");
	}

	@GetMapping("/api/test/voucher-error")
	void voucherError() {
		throw new BusinessException(ErrorCode.VOUCHER_INVALID, "Voucher đã hết hạn", Map.of("reason", "EXPIRED"));
	}

	@PostMapping("/api/test/validate")
	void validate(@Valid @RequestBody PassengerName body) {
	}

	@GetMapping("/api/test/unexpected-error")
	void unexpectedError() {
		throw new IllegalStateException("chi tiết nội bộ không được lộ ra");
	}

	@GetMapping("/api/test/typed")
	int typed(@RequestParam int page) {
		return page;
	}

	record PassengerName(@NotBlank String lastName) {
	}

}
```

Controller này không được gắn `@Import` hay đặt lồng trong lớp test. Lớp lồng trong test bị Spring Boot loại khỏi component scan, và mỗi tổ hợp `@Import` khác nhau sẽ tạo thêm một Spring context cùng một container PostgreSQL mới.

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/common/web/ErrorHandlingTest.java`:

```java
package vn.edu.uit.flightbooking.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.uit.flightbooking.IntegrationTest;

@IntegrationTest
@WithMockUser
class ErrorHandlingTest {

	@Autowired
	MockMvc mvc;

	@Test
	void businessExceptionBecomesProblemDetailWithCode() throws Exception {
		mvc.perform(get("/api/test/business-error"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"))
			.andExpect(jsonPath("$.detail").value("Chuyến VN1825 hạng ECONOMY không còn đủ 3 ghế"))
			.andExpect(jsonPath("$.instance").value("/api/test/business-error"));
	}

	@Test
	void extraPropertiesAreCopiedIntoProblemDetail() throws Exception {
		mvc.perform(get("/api/test/voucher-error"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VOUCHER_INVALID"))
			.andExpect(jsonPath("$.reason").value("EXPIRED"));
	}

	@Test
	void invalidBodyListsFieldErrors() throws Exception {
		mvc.perform(post("/api/test/validate").with(csrf(mvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lastName\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("lastName"));
	}

	@Test
	void malformedJsonIsValidationFailed() throws Exception {
		mvc.perform(post("/api/test/validate").with(csrf(mvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void unknownRouteIsResourceNotFound() throws Exception {
		mvc.perform(get("/api/test/no-such-route"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void wrongParameterTypeIsValidationFailed() throws Exception {
		mvc.perform(get("/api/test/typed").param("page", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void wrongHttpMethodStillHasCode() throws Exception {
		mvc.perform(post("/api/test/business-error").with(csrf(mvc)))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void unexpectedExceptionHidesInternalDetail() throws Exception {
		mvc.perform(get("/api/test/unexpected-error"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.detail", not(containsString("nội bộ"))));
	}

}
```

- [ ] **Step 2: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=ErrorHandlingTest
```

Expected: `Tests run: 8, Failures: 5, Errors: 1`. Hai test về `BusinessException` đã xanh nhờ Task 2. 5 test báo `No value at JSON path "$.code"`. Test lỗi không lường trước báo `ServletException: Request processing failed`.

- [ ] **Step 3: Hoàn thiện bộ xử lý lỗi**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/common/web/GlobalExceptionHandler.java`:

```java
package vn.edu.uit.flightbooking.common.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** Chuyển mọi lỗi thành ProblemDetail (RFC 9457) có thêm trường {@code code} (TDD §5.3, §9). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	ProblemDetail handleBusiness(BusinessException ex) {
		ProblemDetail problem = problem(ex.code(), ex.getMessage());
		ex.properties().forEach(problem::setProperty);
		return problem;
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Lỗi không lường trước", ex);
		return problem(ErrorCode.INTERNAL_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ");
		problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
				.map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
				.toList());
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	/** Lỗi của Spring MVC (404 không có route, JSON sai cú pháp, sai method...) cũng phải có {@code code}. */
	@Override
	protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
			problem.setProperty("code", fallbackCode(statusCode).name());
		}
		return super.createResponseEntity(body, headers, statusCode, request);
	}

	private static ErrorCode fallbackCode(HttpStatusCode status) {
		if (status.value() == 404) {
			return ErrorCode.RESOURCE_NOT_FOUND;
		}
		return status.is4xxClientError() ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR;
	}

	private static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		problem.setProperty("code", code.name());
		return problem;
	}

}
```

Ghi chú:
- `ResponseEntityExceptionHandler` đã xử lý sẵn các lỗi của Spring MVC: 404 không có route, JSON sai cú pháp, tham số sai kiểu, sai method.
- Mọi lỗi kể trên đều đi qua `createResponseEntity` ở cuối, nên chỉ cần thêm `code` tại một chỗ.
- `handleUnexpected` ghi log đầy đủ nhưng trả câu chung chung, để không lộ chi tiết nội bộ (NFR-03).

- [ ] **Step 4: Chạy lại test lỗi và test bảo mật**

```bash
cd backend && ./mvnw -B test -Dtest='ErrorHandlingTest,SecurityConfigTest'
```

Expected: `Tests run: 19, Failures: 0, Errors: 0`. Log có một stack trace `Lỗi không lường trước ... IllegalStateException`. Đây là log mong đợi của test `unexpectedExceptionHidesInternalDetail`.

- [ ] **Step 5: Commit**

```bash
git add backend/src
git commit -m "feat(common): return ProblemDetail with code for every error"
```

---

### Task 4: Đọc tham số hệ thống

**Files:**
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/common/SettingKey.java`, `backend/src/main/java/vn/edu/uit/flightbooking/common/SettingsApi.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/common/SettingsApiTest.java`

**Interfaces:**
- Consumes: bảng `system_settings` (Task 1).
- Produces:
  - `enum SettingKey`: 8 hằng của PRD §7, mỗi hằng có `String key()`, `int min()`, `int max()`.
  - `SettingsApi.getInt(SettingKey key): int`, là `@Service` ở gói gốc `common`.
  - Plan 02 dùng `min()` và `max()` để kiểm tra khoảng hợp lệ khi Admin sửa tham số (FR-111).

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/common/SettingsApiTest.java`:

```java
package vn.edu.uit.flightbooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;

@IntegrationTest
class SettingsApiTest {

	@Autowired
	SettingsApi settings;

	@Autowired
	JdbcClient jdbc;

	@Test
	void readsPrdDefaults() {
		assertThat(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES)).isEqualTo(15);
		assertThat(settings.getInt(SettingKey.BOOKING_MIN_HOURS_BEFORE_DEPARTURE)).isEqualTo(3);
		assertThat(settings.getInt(SettingKey.BOOKING_MAX_SEATED_PASSENGERS)).isEqualTo(9);
		assertThat(settings.getInt(SettingKey.PRICING_CHILD_PERCENT)).isEqualTo(90);
		assertThat(settings.getInt(SettingKey.PRICING_INFANT_PERCENT)).isEqualTo(10);
		assertThat(settings.getInt(SettingKey.AFTERSALES_MIN_HOURS_BEFORE_DEPARTURE)).isEqualTo(24);
		assertThat(settings.getInt(SettingKey.SEARCH_MIN_CONNECTION_MINUTES)).isEqualTo(60);
		assertThat(settings.getInt(SettingKey.SEARCH_MAX_CONNECTION_MINUTES)).isEqualTo(720);
	}

	@Test
	void enumMatchesDatabaseKeysAndDefaultsAreInRange() {
		var dbKeys = jdbc.sql("SELECT key FROM system_settings").query(String.class).set();
		assertThat(dbKeys).containsExactlyInAnyOrderElementsOf(
				Arrays.stream(SettingKey.values()).map(SettingKey::key).toList());
		for (SettingKey key : SettingKey.values()) {
			assertThat(settings.getInt(key)).as(key.key()).isBetween(key.min(), key.max());
		}
	}

	@Test
	@Transactional
	void newValueAppliesToTheNextRead() {
		jdbc.sql("UPDATE system_settings SET value = '20' WHERE key = 'booking.hold_minutes'").update();

		assertThat(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES)).isEqualTo(20);
	}

}
```

Test thứ ba kiểm tra nền tảng của BR-25: giá trị mới có hiệu lực ngay ở lần đọc kế tiếp. Kịch bản đầy đủ "booking tạo sau dùng giá trị mới" (D9) thuộc Plan 11.

- [ ] **Step 2: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=SettingsApiTest
```

Expected: `COMPILATION ERROR` với `cannot find symbol` cho `class SettingsApi` và `SettingKey`.

- [ ] **Step 3: Viết enum tham số**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/SettingKey.java`:

```java
package vn.edu.uit.flightbooking.common;

/** Tham số nghiệp vụ trong bảng system_settings, kèm khoảng hợp lệ (PRD §7). */
public enum SettingKey {

	BOOKING_HOLD_MINUTES("booking.hold_minutes", 5, 60),
	BOOKING_MIN_HOURS_BEFORE_DEPARTURE("booking.min_hours_before_departure", 0, 72),
	BOOKING_MAX_SEATED_PASSENGERS("booking.max_seated_passengers", 1, 9),
	PRICING_CHILD_PERCENT("pricing.child_percent", 0, 100),
	PRICING_INFANT_PERCENT("pricing.infant_percent", 0, 100),
	AFTERSALES_MIN_HOURS_BEFORE_DEPARTURE("aftersales.min_hours_before_departure", 0, 168),
	SEARCH_MIN_CONNECTION_MINUTES("search.min_connection_minutes", 30, 600),
	SEARCH_MAX_CONNECTION_MINUTES("search.max_connection_minutes", 60, 1440);

	private final String key;

	private final int min;

	private final int max;

	SettingKey(String key, int min, int max) {
		this.key = key;
		this.min = min;
		this.max = max;
	}

	public String key() {
		return key;
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}

}
```

- [ ] **Step 4: Viết API đọc tham số**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/SettingsApi.java`:

```java
package vn.edu.uit.flightbooking.common;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Đọc tham số hệ thống. Module khác gọi {@link #getInt} ngay lúc tạo giao dịch,
 * nên giá trị mới chỉ áp dụng cho giao dịch tạo sau đó (BR-25).
 */
@Service
public class SettingsApi {

	private final JdbcClient jdbc;

	SettingsApi(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ponytail: đọc DB mỗi lần (1 lần tra khoá chính, ~1 ms), không cache nên không bao giờ cũ;
	// thêm cache trong bộ nhớ (TDD §6.9) nếu đo thấy chậm.
	public int getInt(SettingKey key) {
		return jdbc.sql("SELECT value FROM system_settings WHERE key = ?")
			.param(key.key())
			.query(Integer.class)
			.single();
	}

}
```

Plan dùng `JdbcClient` thay vì entity JPA vì bảng chỉ có cặp khoá–giá trị. Hơn nữa, `key` và `value` là từ khoá trong JPQL, dùng làm tên field của entity dễ gây lỗi truy vấn.

- [ ] **Step 5: Chạy lại test**

```bash
cd backend && ./mvnw -B test -Dtest=SettingsApiTest
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "feat(common): add SettingKey and SettingsApi for system settings"
```

---

### Task 5: Ranh giới module và CI

**Files:**
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/common/package-info.java`, `.github/workflows/ci.yml`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/ModularityTest.java`

**Interfaces:**
- Consumes: toàn bộ mã ở Task 1–4.
- Produces:
  - `ModularityTest`: chặn phụ thuộc vòng và chặn module truy cập gói nội bộ của module khác. Đây là cổng chặn cho mọi plan sau.
  - Job CI `backend` chạy `./mvnw -B verify` ở mỗi lần push và mỗi pull request.

- [ ] **Step 1: Viết test ranh giới module**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/ModularityTest.java`:

```java
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
```

- [ ] **Step 2: Khai báo `common` là module mở**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/package-info.java`:

```java
/**
 * Hạ tầng dùng chung: mã lỗi, ProblemDetail, bảo mật, tham số hệ thống.
 * Module mở: mọi module khác được dùng mọi lớp ở đây.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package vn.edu.uit.flightbooking.common;

import org.springframework.modulith.ApplicationModule;
```

- [ ] **Step 3: Chạy toàn bộ test**

```bash
cd backend && ./mvnw -B clean verify
```

Expected: `BUILD SUCCESS` với 26 test: `ModularityTest` 1, `MigrationTest` 3, `SettingsApiTest` 3, `ErrorHandlingTest` 8, `SecurityConfigTest` 11. Hiện mới chỉ có module `common` nên `ModularityTest` xanh ngay. Test này có giá trị từ Plan 02 trở đi, khi các module nghiệp vụ được thêm vào.

- [ ] **Step 4: Viết workflow CI**

Tạo `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - run: ./mvnw -B verify
```

Runner Ubuntu của GitHub có sẵn Docker, nên Testcontainers chạy được mà không cần cấu hình thêm (TDD §11.3). Plan 03 sẽ thêm job `frontend`.

- [ ] **Step 5: Kiểm tra cú pháp workflow (tuỳ chọn, cần Docker)**

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/repo" -w /repo rhysd/actionlint:latest -color=false
```

Expected: không in gì và exit code 0. `MSYS_NO_PATHCONV=1` và `pwd -W` chỉ cần trên Git Bash của Windows; trên macOS/Linux thì dùng `$(pwd)`.

- [ ] **Step 6: Commit**

```bash
git add backend/src .github/workflows/ci.yml
git commit -m "ci: verify module boundaries and run backend tests on every push"
```

- [ ] **Step 7: Xác nhận CI sau khi push**

Bước này chỉ chạy khi nhánh đã được push lên GitHub:

```bash
gh run watch "$(gh run list --commit "$(git rev-parse HEAD)" --limit 1 --json databaseId -q '.[0].databaseId')" --exit-status
```

Expected: job `backend` thành công. GitHub cần vài giây sau khi push mới tạo run. Nếu lệnh báo thiếu run ID thì chờ một chút rồi chạy lại.

Nếu job báo `./mvnw: Permission denied`, quay lại Task 1 Step 10 và chạy `git update-index --chmod=+x backend/mvnw`.

---

### Task 6: Docker Compose, Dockerfile và lệnh cho dev

**Files:**
- Create: `.env.example`, `docker-compose.yml`, `backend/Dockerfile`, `backend/.dockerignore`
- Modify: `CLAUDE.md` (mục Status, thêm mục Commands)

**Interfaces:**
- Consumes: ứng dụng ở Task 1–5.
- Produces:
  - `docker-compose.yml` có 3 service `postgres`, `mailpit`, `backend` (Plan 03 thêm `frontend`).
  - `.env.example` là hợp đồng biến môi trường; các plan sau thêm biến của mình vào đây (`MAIL_*`, `APP_BASE_URL`, `VNPAY_*`, `BACKEND_URL`).

- [ ] **Step 1: Viết `.env.example` và tạo `.env`**

Tạo `.env.example`:

```properties
# Sao chép thành .env rồi sửa giá trị (.env không được commit).
# Giá trị ở đây dùng khi chạy backend trực tiếp trên máy (profile dev đọc file này);
# docker-compose.yml tự đổi host sang tên service (postgres, mailpit, ...).

SPRING_PROFILES_ACTIVE=dev

# Đổi DB_PORT và cổng trong DB_URL nếu máy đã có PostgreSQL khác chiếm cổng 5432.
DB_PORT=5432
DB_URL=jdbc:postgresql://localhost:5432/flightbooking
DB_USERNAME=flightbooking
DB_PASSWORD=change-me

# Bật khi chạy sau HTTPS để cookie phiên có cờ Secure (NFR-03).
COOKIE_SECURE=false
```

Tạo `.env` và kiểm tra cổng 5432:

```bash
cp .env.example .env
netstat -ano | grep LISTENING | grep ':5432 ' || echo "5432 trống"
```

Nếu cổng 5432 đã bị chiếm (VD PostgreSQL cài dạng Windows service), sửa `.env` thành `DB_PORT=5433` và `DB_URL=jdbc:postgresql://localhost:5433/flightbooking`. Đổi luôn `DB_PASSWORD` thành một mật khẩu riêng.

`.gitignore` ở thư mục gốc đã bỏ qua `.env` và giữ lại `.env.example`.

- [ ] **Step 2: Viết `docker-compose.yml`**

Tạo `docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: flightbooking
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "${DB_PORT:-5432}:5432"
    volumes:
      - pgdata:/var/lib/postgresql   # postgres:18 lưu dữ liệu ở /var/lib/postgresql/18/docker
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10

  mailpit:
    image: axllent/mailpit
    ports:
      - "1025:1025"
      - "8025:8025"

  backend:
    build: ./backend
    env_file: .env
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/flightbooking
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s

volumes:
  pgdata:
```

Ghi chú:
- **Volume của PostgreSQL 18:** image `postgres:18` đổi chỗ lưu dữ liệu. Phải mount `/var/lib/postgresql`, không phải `/var/lib/postgresql/data` như các bản trước.
- **Hostname trong container:** service `backend` ghi đè `DB_URL` để dùng hostname `postgres` trong mạng Docker, còn `.env` giữ `localhost` để chạy backend trực tiếp trên máy.

- [ ] **Step 3: Viết Dockerfile cho backend**

Tạo `backend/Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sh mvnw -B -q dependency:go-offline
COPY src src
RUN sh mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/flightbooking-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Tạo `backend/.dockerignore`:

```
target/
```

Ghi chú:
- **Lớp dependency được cache.** Lớp `dependency:go-offline` chỉ build lại khi `pom.xml` đổi, nên sửa code không phải tải lại dependency.
- **Dùng `sh mvnw`** để không phụ thuộc quyền thực thi của file khi build từ máy Windows.
- **Có sẵn `curl`.** Image `eclipse-temurin:21-jre` có `curl`, nên healthcheck của compose dùng được.

- [ ] **Step 4: Chạy cả stack và kiểm tra**

```bash
docker compose up -d --build
docker compose ps
curl -s localhost:8080/actuator/health; echo
curl -s localhost:8080/api/me; echo
curl -s -o /dev/null -w "api-docs: %{http_code}\n" localhost:8080/v3/api-docs
```

Expected, sau khoảng 30 giây cho lần build đầu:
- `docker compose ps`: cả `postgres`, `mailpit` và `backend` đều `(healthy)`.
- `/actuator/health` trả `{"groups":["liveness","readiness"],"status":"UP"}`.
- `/api/me` trả `{"detail":"Bạn chưa đăng nhập hoặc phiên đã hết hạn","instance":"/api/me","status":401,"title":"Unauthorized","code":"UNAUTHENTICATED"}`.
- Dòng cuối in `api-docs: 200`, vì `.env` đặt `SPRING_PROFILES_ACTIVE=dev`.

Nếu `backend` không lên được, xem log bằng `docker compose logs backend`.

- [ ] **Step 5: Chạy backend trực tiếp trên máy**

Đây là cách dev hằng ngày theo TDD §10.2: chỉ PostgreSQL và Mailpit chạy trong Docker, backend chạy bằng Maven.

```bash
docker compose stop backend
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Expected trong log:
- `The following 1 profile is active: "dev"`
- `Database: jdbc:postgresql://localhost:<DB_PORT>/flightbooking (PostgreSQL 18.x)`
- `Started FlightBookingApplication`

Mở <http://localhost:8080/swagger-ui.html>, trang Swagger UI hiện ra. Dừng backend bằng `Ctrl+C`.

- [ ] **Step 6: Cập nhật CLAUDE.md**

Trong `CLAUDE.md`, mục `## Status`, thay câu:

```
No application code, build system, or tests exist yet. Add build/test commands here once the code is scaffolded.
```

bằng:

```
Backend foundation lives in `backend/` (Plan 01); the frontend is not scaffolded yet. Roadmap and plans: `docs/superpowers/plans/`.
```

Thêm mục sau ngay sau mục `## Status`:

```markdown
## Commands

Copy `.env.example` to `.env` first (change `DB_PORT`/`DB_URL` if port 5432 is taken). Docker Desktop must be running: tests use Testcontainers.

- Dev infra: `docker compose up -d postgres mailpit` (Mailpit UI: http://localhost:8025)
- Backend tests: `cd backend && ./mvnw verify`
- One test class: `cd backend && ./mvnw test -Dtest=SecurityConfigTest`
- Run backend (profile `dev`, reads `../.env`): `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, Swagger at http://localhost:8080/swagger-ui.html
- Full stack: `docker compose up -d --build`

Test conventions: integration tests use `@IntegrationTest` (one shared Spring context and one PostgreSQL 18 container). Send CSRF with `TestCsrf.csrf(mvc)`, never `SecurityMockMvcRequestPostProcessors.csrf()`: it swaps the shared `CsrfFilter`'s token repository and breaks later tests. Tests create their own data and never read `.env`.
```

- [ ] **Step 7: Commit**

```bash
git add .env.example docker-compose.yml backend/Dockerfile backend/.dockerignore CLAUDE.md
git status --short
git commit -m "build: add Docker Compose stack, backend Dockerfile and dev commands"
```

Expected: `git status --short` **không** liệt kê `.env`.

- [ ] **Step 8: Dừng stack**

```bash
docker compose down
```

Lệnh này giữ nguyên volume dữ liệu. Muốn xoá sạch dữ liệu thì thêm `-v`.

---

## Kiểm tra cuối plan

- [ ] `cd backend && ./mvnw -B clean verify` → `BUILD SUCCESS`, 26 test.
- [ ] `docker compose up -d --build` → 3 service `(healthy)`.
- [ ] CI trên GitHub xanh (nếu đã push).
- [ ] Đánh dấu Plan 01 ở [Lộ trình §2](2026-10-09-00-roadmap.md#2-danh-sách-plan) và đối chiếu bảng hợp đồng ở §6 với code thật.
