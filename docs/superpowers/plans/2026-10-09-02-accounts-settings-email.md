# Plan 02 — Tài khoản, phiên đăng nhập, tham số, khung email: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Người dùng đăng ký, đăng nhập, đăng xuất, sửa hồ sơ, đổi và đặt lại mật khẩu qua email. Admin quản lý tài khoản (tìm, tạo Staff/Admin, khoá/mở khoá) và tham số hệ thống. Có khung gửi email cho các plan sau và 3 tài khoản mẫu cho profile `dev`.

**Architecture:** Thêm hai module Spring Modulith là `identity` (tài khoản, phiên đăng nhập, đặt lại mật khẩu) và `notification` (khung email). `common` có thêm `Role`, `CurrentUser` và API quản trị tham số. Phiên đăng nhập là session cookie; principal trong session là record `CurrentUser(id, role)`, controller nhận bằng `@AuthenticationPrincipal`. Email đặt lại mật khẩu đi qua sự kiện `PasswordResetRequested`. Listener `@ApplicationModuleListener` ở `notification` gửi email bất đồng bộ sau khi transaction commit.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Spring Security 7.1, Spring Data JPA, Hibernate 7), Spring Modulith 2.1.1 (`spring-modulith-events-api`), Thymeleaf, Jakarta Mail (Angus), Flyway, PostgreSQL 18, Testcontainers, MockMvc, Awaitility, Mailpit.

**Spec:** [PRD](../../PRD.md) FR-01–04, FR-110, FR-111, FR-130, BR-25, BR-100–105, §7 · [TDD](../../TDD.md) §3.3, §4.1, §4.4, §6.9, §6.10, §6.12, §8.1, §8.7, §9, §10.1 · [App Flow](../../APP_FLOW.md) C-03–C-06, C-12, A-08, A-09, UF-06, §4.7 · [Backend Schema](../../BACKEND_SCHEMA.md) §5 (`users`, `password_reset_tokens`, `system_settings`), §8.5 · [Lộ trình](2026-10-09-00-roadmap.md) §5, §6

## Global Constraints

- Mọi ràng buộc ở Global Constraints của [Plan 01](2026-10-09-01-backend-foundation.md) vẫn giữ nguyên: phiên bản thư viện, ProblemDetail có `code`, CSRF SPA, câu lỗi tiếng Việt, `ddl-auto=validate`.
- Phụ thuộc giữa module: `identity → common`, `notification → identity, common`. `common` không phụ thuộc module nào, `ModularityTest` kiểm tra điều này.
- DTO là Java `record`. Entity dùng Lombok `@Getter`/`@Setter`, không dùng `@Data` (TDD §2).
- **Mật khẩu (BR-100):** ít nhất 8 ký tự, có cả chữ và số, không dài quá 72 byte UTF-8 (giới hạn của BCrypt). DB chỉ lưu hash BCrypt.
- **Email (BR-100):** bỏ khoảng trắng hai đầu và đổi sang chữ thường trước khi lưu hoặc so sánh. DB có `CHECK (email = lower(email))`.
- **Phiên (TDD §4.1):** principal là `CurrentUser(id, role)`, quyền là `ROLE_<role>`. Đăng nhập thì đổi session id rồi lưu `SecurityContext` vào `HttpSession`. Phiên hết hạn sau 30 phút (đã cấu hình ở Plan 01).
- **Token đặt lại mật khẩu (TDD §4.4):** 32 byte `SecureRandom`, mã hoá Base64URL không padding. DB chỉ lưu SHA-256 dạng hex. Token hết hạn sau 30 phút và chỉ dùng được một lần. Link có dạng `{APP_BASE_URL}/reset-password?token=...`. `forgot-password` luôn trả 200.
- **Email (TDD §6.10, FR-130):** gửi bất đồng bộ sau commit qua `@ApplicationModuleListener`, template đặt ở `templates/email/<tên>.html`. Gửi lỗi thì chỉ ghi log `ERROR`.
- **Tham số (PRD §7):** giá trị phải nằm trong khoảng hợp lệ của `SettingKey`. Thời gian nối chuyến tối thiểu phải nhỏ hơn tối đa.
- **Không ghi token hay mật khẩu vào log (NFR-03).** `PasswordResetRequested.toString()` giấu token.
- Seed `db/seed` chỉ nạp ở profile `dev`. Test không dựa vào seed.
- BR-104 (Customer chỉ thấy booking của mình) ở plan này chỉ dừng ở việc chuẩn bị `CurrentUser.id()`. Kiểm tra quyền sở hữu thật làm ở Plan 11 (test T15).

### Điểm cố ý khác TDD

Ba điểm dưới đây khác TDD. Nếu không đồng ý điểm nào, hãy báo lại để sửa plan trước khi làm.

1. **Đăng nhập không dùng `AuthenticationManager`/`DaoAuthenticationProvider`** (TDD §4.1 bước 1). `AccountService.authenticate` tự kiểm tra BCrypt. Lý do: `DaoAuthenticationProvider` kiểm tra khoá **trước** mật khẩu, nên ai chỉ biết email cũng biết được tài khoản đó đang bị khoá. Plan này kiểm tra mật khẩu trước, sai thì luôn báo `INVALID_CREDENTIALS`. Email không tồn tại vẫn chạy BCrypt với một hash giả, để thời gian phản hồi không lộ email. Bước 2 (đổi session id) và bước 3 (lưu vào `HttpSession`) giữ đúng như TDD.
2. **Chặn phiên của tài khoản bị khoá bằng `HandlerInterceptor` của Spring MVC, không bằng filter của Spring Security** (TDD §4.1). `SecurityConfig` nằm ở `common`, mà `common` không được phụ thuộc `identity`. Hệ quả: request bị chặn 403 vì sai vai trò, hoặc gọi đường dẫn không tồn tại (404), không huỷ phiên. Phiên chỉ bị huỷ ở request kế tiếp gọi vào một API có thật.
3. **Nhập sai mật khẩu hiện tại ở `PUT /me/password` trả 400 `VALIDATION_FAILED`**, kèm `errors[{field: "currentPassword"}]`. TDD §9 không có mã riêng cho trường hợp này. Không dùng 401 vì frontend sẽ hiểu nhầm là phiên đã hết hạn.

Những điểm TDD không nói, plan tự chọn:
- Admin tự khoá chính mình (BR-105) trả 409 `INVALID_STATE`.
- Admin tạo tài khoản `CUSTOMER` trả 400 `VALIDATION_FAILED` cho trường `role` (BR-101).
- Khoá tham số không tồn tại trả 404 `RESOURCE_NOT_FOUND`.
- `reset-password` và `PUT /admin/settings/{key}` trả 204.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng đã có test ghim lại.

1. **Mật khẩu có dấu dài quá 72 byte** (VD 25 chữ "ệ" và một chữ số: 26 ký tự nhưng 76 byte) phải trả 400 cho trường `password`, không được thành 500. BCrypt của Spring Security 7.1 ném lỗi khi băm mật khẩu như vậy (Task 1, `passwordOverBcryptLimitIsValidationErrorNotServerError`).
2. **Phiên đang mở của tài khoản vừa bị khoá:** request kế tiếp nhận 401 `ACCOUNT_LOCKED`, và phiên bị huỷ nên request sau nữa nhận `UNAUTHENTICATED` (Task 3, `lockingEndsTheUsersOpenSessionOnNextRequest`).
3. **Email khác hoa thường hoặc có khoảng trắng** vẫn là cùng một tài khoản. Đăng ký `MIXED.CASE@...` khi đã có `Mixed.Case@...` trả 409. Lỗi này đến từ ràng buộc UNIQUE nên hai request đăng ký cùng lúc cũng ra 409 (Task 1, `emailIsCaseInsensitive`, `loginAcceptsEmailInAnyCase`).
4. **Thiếu `value` khi sửa tham số** phải trả 400, không được hiểu thành 0. `pricing.child_percent = 0` nằm trong khoảng hợp lệ nên lỗi này sẽ không ai thấy (Task 4, `missingValueIsValidationErrorNotZero`).
5. **`?sort=` lạ ở danh sách tài khoản** (`?sort=khong_co`) vẫn trả 200 và bỏ qua sort, không thành 500 (Task 3, `searchesByEmailOrNameAndFiltersByRole`).

---

## Trước khi bắt đầu

- **Cần có:** Docker Desktop đang chạy, Git Bash, JDK 21 trở lên. Plan 01 đã xong.
- **Chạy lệnh ở đâu:** mọi lệnh dùng Git Bash và chạy từ thư mục gốc repo, trừ khi bước đó ghi `cd backend`.
- **Nhánh:** tạo nhánh mới từ `develop`, VD `feat/02-accounts`.
- **Đã kiểm chứng:** toàn bộ code và lệnh trong plan đã chạy thử ngày 2026-10-09 trên Windows 11 với JDK 25 và Docker 29. Các task được làm lại lần lượt từ 1 đến 7: mỗi task đỏ rồi xanh đúng như ghi trong plan. Cuối plan có 59 test xanh; `docker compose up` chạy cả 3 service healthy, đăng nhập được bằng tài khoản seed, và email đặt lại mật khẩu xuất hiện trong Mailpit.
- **Cảnh báo vô hại trong log:**
  - Khi chạy test: `HHH000247` và `duplicate key value violates unique constraint "users_email_key"`. Đây là test đăng ký trùng email, cố ý gây ra.
  - Khi chạy profile `dev`: `outOfOrder mode is active`. Cấu hình này là cố ý (Task 7).

## Cấu trúc file sau plan

```
/
├── .env.example                            # Task 6: MAIL_*, APP_BASE_URL
├── docker-compose.yml                      # Task 6: backend dùng mailpit
└── backend/
    ├── pom.xml                             # Task 6: spring-modulith-events-api
    └── src/
        ├── main/java/vn/edu/uit/flightbooking/
        │   ├── FlightBookingApplication.java          # Task 6: @EnableAsync
        │   ├── common/
        │   │   ├── Role.java, CurrentUser.java        # Task 1
        │   │   ├── BusinessException.java             # Task 2: invalidField()
        │   │   ├── SettingKey.java                    # Task 4: of()
        │   │   ├── SettingsApi.java                   # Task 4: list(), update()
        │   │   └── web/AdminSettingsController.java   # Task 4
        │   ├── identity/
        │   │   ├── UserApi.java, UserSummary.java     # Task 6 (API công khai)
        │   │   ├── PasswordResetRequested.java        # Task 5 (sự kiện)
        │   │   ├── domain/
        │   │   │   ├── User.java, UserStatus.java     # Task 1
        │   │   │   ├── AccountService.java            # Task 1, thêm ở Task 2, 3, 5
        │   │   │   └── PasswordResetService.java      # Task 5
        │   │   ├── infra/UserRepository.java          # Task 1, thêm ở Task 3
        │   │   └── web/
        │   │       ├── Password.java, UserResponse.java          # Task 1
        │   │       ├── AuthController.java                       # Task 1, thêm ở Task 5
        │   │       ├── MeController.java                         # Task 1, thêm ở Task 2
        │   │       ├── AdminUserController.java                  # Task 3
        │   │       └── LockedAccountInterceptor.java             # Task 3
        │   └── notification/
        │       ├── infra/Mailer.java                  # Task 6
        │       └── domain/PasswordResetEmail.java     # Task 6
        ├── main/resources/
        │   ├── application.properties                 # Task 1, Task 6
        │   ├── application-dev.properties             # Task 7
        │   ├── templates/email/password-reset.html    # Task 6
        │   └── db/seed/V105__seed_accounts.sql        # Task 7
        └── test/java/vn/edu/uit/flightbooking/
            ├── IntegrationTest.java                   # Task 6: thêm TestMailSender
            ├── TestUsers.java                         # Task 1
            ├── TestMailSender.java                    # Task 6
            ├── common/web/AdminSettingsControllerTest.java   # Task 4
            ├── identity/
            │   ├── SeedAccountsTest.java              # Task 7
            │   └── web/
            │       ├── AuthControllerTest.java        # Task 1
            │       ├── MeControllerTest.java          # Task 2
            │       ├── AdminUserControllerTest.java   # Task 3
            │       └── PasswordResetTest.java         # Task 5
            └── notification/PasswordResetEmailTest.java      # Task 6
```

---

### Task 1: Đăng ký, đăng nhập, đăng xuất và `GET /me`

**Files:**
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/common/Role.java`, `.../common/CurrentUser.java`, `.../identity/domain/User.java`, `.../identity/domain/UserStatus.java`, `.../identity/domain/AccountService.java`, `.../identity/infra/UserRepository.java`, `.../identity/web/Password.java`, `.../identity/web/UserResponse.java`, `.../identity/web/AuthController.java`, `.../identity/web/MeController.java`
- Modify: `backend/src/main/resources/application.properties`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/TestUsers.java`, `backend/src/test/java/vn/edu/uit/flightbooking/identity/web/AuthControllerTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest`, `TestCsrf.csrf(mvc)`, `BusinessException`, `ErrorCode`, `SecurityConfig` (Plan 01).
- Produces:
  - `enum common.Role { CUSTOMER, STAFF, ADMIN }`.
  - `record common.CurrentUser(long id, Role role)`: principal trong session; controller nhận bằng `@AuthenticationPrincipal CurrentUser me`.
  - Entity `identity.domain.User` (`id`, `email`, `passwordHash`, `fullName`, `phone`, `role`, `status`, `updatedAt`) và `enum UserStatus { ACTIVE, LOCKED }`.
  - `UserRepository.findByEmail(String) → Optional<User>`.
  - `AccountService`: `User register(String email, String password, String fullName, String phone)`, `User authenticate(String email, String password)`, `User get(long id)`.
  - Trong gói `identity.web`: constraint `@Password` và `record UserResponse(long id, String email, String fullName, String phone, Role role, UserStatus status)` có `static UserResponse of(User)`.
  - API: `POST /api/auth/register` (201), `POST /api/auth/login` (200), `POST /api/auth/logout` (204), `GET /api/me` (200). Cả ba API đăng ký, đăng nhập và `/me` đều trả `UserResponse`.
  - Test helper `TestUsers`: hằng `PASSWORD`; các hàm `String randomEmail()`, `long create(JdbcClient, Role, String email)`, `TestUser loggedIn(MockMvc, JdbcClient, Role)`, `MockHttpSession login(MockMvc, String email, String password)`; record `TestUser(long id, String email, MockHttpSession session)`.

- [ ] **Step 1: Viết test helper `TestUsers`**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/TestUsers.java`:

```java
package vn.edu.uit.flightbooking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.uit.flightbooking.common.Role;

/**
 * Tạo tài khoản và đăng nhập thật qua {@code POST /api/auth/login}, để test đi qua đúng đường của người dùng.
 * Gửi session trả về bằng {@code .session(user.session())}.
 */
public final class TestUsers {

	public static final String PASSWORD = "Password123";

	private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);

	public record TestUser(long id, String email, MockHttpSession session) {
	}

	private TestUsers() {
	}

	/** Email ngẫu nhiên để không trùng giữa các test, kể cả test đã commit dữ liệu. */
	public static String randomEmail() {
		return "user-" + UUID.randomUUID() + "@test.local";
	}

	/** Tài khoản ACTIVE với mật khẩu {@link #PASSWORD}. */
	public static long create(JdbcClient jdbc, Role role, String email) {
		return jdbc.sql("""
				INSERT INTO users (email, password_hash, full_name, phone, role)
				VALUES (?, ?, 'Người Dùng Test', '0900000000', ?)
				RETURNING id
				""")
			.params(email, PASSWORD_HASH, role.name())
			.query(Long.class)
			.single();
	}

	public static TestUser loggedIn(MockMvc mvc, JdbcClient jdbc, Role role) throws Exception {
		String email = randomEmail();
		long id = create(jdbc, role, email);
		return new TestUser(id, email, login(mvc, email, PASSWORD));
	}

	public static MockHttpSession login(MockMvc mvc, String email, String password) throws Exception {
		return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf(mvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}
						""".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn()
			.getRequest()
			.getSession(false);
	}

}
```

Ghi chú:
- **Vì sao tạo user bằng SQL:** nhanh, và không phụ thuộc API đăng ký đang được test.
- **Dùng chung transaction với test:** `JdbcClient` tham gia transaction của test `@Transactional`, và MockMvc chạy cùng thread, nên request đăng nhập thấy được user vừa tạo. Hết test thì mọi thứ được rollback.

- [ ] **Step 2: Viết test đăng ký, đăng nhập, đăng xuất**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/identity/web/AuthControllerTest.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

@IntegrationTest
@Transactional
class AuthControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	ResultActions register(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/register").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s", "fullName": "Nguyễn Văn A", "phone": "0901234567"}
					""".formatted(email, password)));
	}

	ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s"}
					""".formatted(email, password)));
	}

	@Test
	void registerCreatesActiveCustomerAndSignsIn() throws Exception {
		var session = (MockHttpSession) register("new.customer@test.local", "Matkhau123")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("new.customer@test.local"))
			.andExpect(jsonPath("$.role").value("CUSTOMER"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andReturn().getRequest().getSession(false);

		mvc.perform(get("/api/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));
	}

	@Test
	void emailIsCaseInsensitive() throws Exception {
		register("Mixed.Case@Test.Local", "Matkhau123")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("mixed.case@test.local"));

		register("MIXED.CASE@test.local", "Matkhau123")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
	}

	@Test
	void loginAcceptsEmailInAnyCase() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "case@test.local");

		login("  CASE@Test.Local ", TestUsers.PASSWORD).andExpect(status().isOk());
	}

	@Test
	void weakPasswordIsRejectedPerField() throws Exception {
		register("weak@test.local", "chicochu")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("password"));
		register("weak@test.local", "12345678").andExpect(status().isBadRequest());
		register("weak@test.local", "abc123").andExpect(status().isBadRequest());
	}

	@Test
	void passwordOverBcryptLimitIsValidationErrorNotServerError() throws Exception {
		// 25 chữ "ệ" (3 byte mỗi chữ) + số: 26 ký tự nhưng 76 byte, BCrypt sẽ ném lỗi nếu không chặn trước.
		register("long@test.local", "ệ".repeat(25) + "1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("password"));
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "known@test.local");

		login("known@test.local", "SaiMatKhau1")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng"));
		login("unknown@test.local", "SaiMatKhau1")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng"));
	}

	@Test
	void veryLongPasswordAtLoginIsInvalidCredentials() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "known@test.local");

		login("known@test.local", "a1".repeat(100))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void lockedAccountCannotLogIn() throws Exception {
		long id = TestUsers.create(jdbc, Role.CUSTOMER, "locked@test.local");
		jdbc.sql("UPDATE users SET status = 'LOCKED' WHERE id = ?").param(id).update();

		login("locked@test.local", TestUsers.PASSWORD)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
		// Sai mật khẩu thì vẫn chỉ báo INVALID_CREDENTIALS, không lộ là tài khoản đang bị khoá.
		login("locked@test.local", "SaiMatKhau1").andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void loginChangesSessionId() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "fixation@test.local");
		var session = new MockHttpSession();
		String before = session.getId();

		mvc.perform(post("/api/auth/login").with(csrf(mvc)).session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "fixation@test.local", "password": "%s"}
					""".formatted(TestUsers.PASSWORD)))
			.andExpect(status().isOk());

		assertThat(session.getId()).isNotEqualTo(before);
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		var user = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);

		mvc.perform(post("/api/auth/logout").with(csrf(mvc)).session(user.session()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/me").session(user.session()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

}
```

- [ ] **Step 3: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=AuthControllerTest
```

Expected: `BUILD FAILURE` do lỗi biên dịch `cannot find symbol` ở `TestUsers.java` và `AuthControllerTest.java`, vì chưa có `vn.edu.uit.flightbooking.common.Role`.

- [ ] **Step 4: Viết `Role` và `CurrentUser` trong `common`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/Role.java`:

```java
package vn.edu.uit.flightbooking.common;

/** Vai trò của tài khoản (PRD §2). Spring Security dùng dưới dạng {@code ROLE_<tên>}. */
public enum Role {

	CUSTOMER, STAFF, ADMIN

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/CurrentUser.java`:

```java
package vn.edu.uit.flightbooking.common;

/**
 * Người đang gọi API, là principal lưu trong session sau khi đăng nhập.
 * Controller nhận bằng {@code @AuthenticationPrincipal CurrentUser me}.
 */
public record CurrentUser(long id, Role role) {
}
```

`Role` nằm ở `common` vì cả `CurrentUser` (common) lẫn entity `User` (identity) đều dùng. Module sau cần biết ai đang gọi thì chỉ cần `CurrentUser`, không phải phụ thuộc `identity`.

- [ ] **Step 5: Viết entity và repository**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/domain/UserStatus.java`:

```java
package vn.edu.uit.flightbooking.identity.domain;

/** Trạng thái tài khoản (APP_FLOW §4.7). */
public enum UserStatus {

	ACTIVE, LOCKED

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/domain/User.java`:

```java
package vn.edu.uit.flightbooking.identity.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UpdateTimestamp;

import lombok.Getter;
import lombok.Setter;
import vn.edu.uit.flightbooking.common.Role;

/** Bảng {@code users}. Email luôn lưu chữ thường (BR-100, có CHECK trong DB). */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String email;

	private String passwordHash;

	private String fullName;

	private String phone;

	@Enumerated(EnumType.STRING)
	private Role role;

	@Enumerated(EnumType.STRING)
	private UserStatus status = UserStatus.ACTIVE;

	@UpdateTimestamp
	private Instant updatedAt;

}
```

Cột `created_at` không được ánh xạ: DB tự điền giá trị mặc định, và chưa API nào cần đọc cột này. Hibernate `validate` chỉ kiểm tra các cột có ánh xạ.

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/infra/UserRepository.java`:

```java
package vn.edu.uit.flightbooking.identity.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.flightbooking.identity.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

}
```

- [ ] **Step 6: Viết `AccountService`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/domain/AccountService.java`:

```java
package vn.edu.uit.flightbooking.identity.domain;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.identity.infra.UserRepository;

/** Tài khoản: đăng ký, đăng nhập, hồ sơ, mật khẩu (FR-01–04) và quản trị tài khoản (FR-110). */
@Service
public class AccountService {

	private final PasswordEncoder encoder = new BCryptPasswordEncoder();

	/** Email không tồn tại vẫn chạy BCrypt với hash này, để thời gian phản hồi không lộ email nào đã đăng ký. */
	private final String dummyHash = encoder.encode("timing-attack-protection");

	private final UserRepository users;

	AccountService(UserRepository users) {
		this.users = users;
	}

	/** FR-01, BR-101: tự đăng ký luôn là CUSTOMER. */
	@Transactional
	public User register(String email, String password, String fullName, String phone) {
		return create(email, password, fullName, phone, Role.CUSTOMER);
	}

	/** FR-02: sai email và sai mật khẩu báo cùng một lỗi; kiểm tra khoá sau khi mật khẩu đúng (BR-102). */
	@Transactional(readOnly = true)
	public User authenticate(String email, String password) {
		User user = users.findByEmail(normalize(email)).orElse(null);
		String hash = user != null ? user.getPasswordHash() : dummyHash;
		if (!encoder.matches(password, hash) || user == null) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Email hoặc mật khẩu không đúng");
		}
		if (user.getStatus() == UserStatus.LOCKED) {
			throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Tài khoản đã bị khoá");
		}
		return user;
	}

	@Transactional(readOnly = true)
	public User get(long id) {
		return users.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài khoản"));
	}

	private User create(String email, String password, String fullName, String phone, Role role) {
		User user = new User();
		user.setEmail(normalize(email));
		user.setPasswordHash(encoder.encode(password));
		user.setFullName(fullName);
		user.setPhone(phone);
		user.setRole(role);
		try {
			return users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException e) {
			// Bắt lỗi UNIQUE thay vì kiểm tra trước: hai request đăng ký cùng email cùng lúc vẫn ra 409.
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED, "Email đã được đăng ký");
		}
	}

	/** BR-100: email không phân biệt hoa thường. */
	private static String normalize(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
```

Ghi chú:
- **Encoder không phải bean:** chỉ `AccountService` băm mật khẩu, nên không cần khai báo bean `PasswordEncoder`.
- **Mật khẩu quá dài lúc đăng nhập:** BCrypt chỉ ném lỗi khi **băm** mật khẩu dài hơn 72 byte, còn khi **so khớp** thì trả `false`. Vì vậy đăng nhập bằng mật khẩu rất dài ra `INVALID_CREDENTIALS`, test `veryLongPasswordAtLoginIsInvalidCredentials` ghim hành vi này. Các API nhận mật khẩu mới thì chặn bằng `@Password` (Step 7).

- [ ] **Step 7: Viết constraint mật khẩu và DTO trả về**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/web/Password.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * BR-100: ít nhất 8 ký tự, có cả chữ và số. Thêm giới hạn 72 byte vì BCrypt từ chối mật khẩu dài hơn
 * (chữ có dấu chiếm 2–3 byte trong UTF-8).
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Password.Validator.class)
@interface Password {

	String message() default "Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số, và không dài quá 72 byte";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<Password, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value != null && value.length() >= 8
					&& value.getBytes(StandardCharsets.UTF_8).length <= 72
					&& value.chars().anyMatch(Character::isLetter)
					&& value.chars().anyMatch(Character::isDigit);
		}

	}

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/web/UserResponse.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.identity.domain.User;
import vn.edu.uit.flightbooking.identity.domain.UserStatus;

/** Tài khoản trả về cho đăng nhập, {@code /me} và màn hình A-08. Không bao giờ chứa hash mật khẩu. */
record UserResponse(long id, String email, String fullName, String phone, Role role, UserStatus status) {

	static UserResponse of(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole(),
				user.getStatus());
	}

}
```

- [ ] **Step 8: Viết controller đăng nhập và `/me`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/web/AuthController.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.identity.domain.AccountService;
import vn.edu.uit.flightbooking.identity.domain.User;

/** Đăng ký, đăng nhập, đăng xuất, quên mật khẩu (FR-01–03, TDD §4.1, §4.4). */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	record RegisterRequest(@NotBlank @Email @Size(max = 255) String email, @Password String password,
			@NotBlank @Size(max = 100) String fullName, @NotBlank @Size(max = 20) String phone) {
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	private final AccountService accounts;

	AuthController(AccountService accounts) {
		this.accounts = accounts;
	}

	/** FR-01: đăng ký xong thì đăng nhập luôn. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User user = accounts.register(body.email(), body.password(), body.fullName(), body.phone());
		signIn(user, request, response);
		return UserResponse.of(user);
	}

	@PostMapping("/login")
	UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User user = accounts.authenticate(body.email(), body.password());
		signIn(user, request, response);
		return UserResponse.of(user);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

	/** TDD §4.1: đổi session id (chống session fixation) rồi lưu SecurityContext vào HttpSession. */
	private void signIn(User user, HttpServletRequest request, HttpServletResponse response) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		var principal = new CurrentUser(user.getId(), user.getRole());
		var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}

}
```

Ghi chú:
- **Đăng xuất là một controller, không dùng `http.logout()`.** `LogoutFilter` chạy trước bước phân quyền, nên khách chưa đăng nhập gọi logout sẽ nhận 204. Như vậy test `anonymousUserGetsUnauthenticatedProblem` của Plan 01, vốn cần 401, sẽ hỏng.
- **Sau khi đăng nhập, frontend gọi `GET /api/auth/csrf`** như APP_FLOW C-03 đã ghi. Cookie `XSRF-TOKEN` không gắn với session, nên cookie cũ vẫn dùng được.

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/web/MeController.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.identity.domain.AccountService;

/** Màn hình C-12 (FR-04). */
@RestController
@RequestMapping("/api/me")
class MeController {

	private final AccountService accounts;

	MeController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	UserResponse me(@AuthenticationPrincipal CurrentUser me) {
		return UserResponse.of(accounts.get(me.id()));
	}

}
```

- [ ] **Step 9: Tắt user sinh sẵn của Spring Boot**

Thêm vào cuối `backend/src/main/resources/application.properties`:

```properties

# Đăng nhập do identity tự làm (Plan 02), không cần user sinh sẵn của Spring Boot.
spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
```

Thiếu dòng này thì log in ra `Using generated security password: ...` mỗi lần khởi động (Plan 01 đã ghi nhận). Tên lớp trên là của Spring Boot 4; ở Boot 3, lớp này nằm ở gói khác.

- [ ] **Step 10: Chạy lại test**

```bash
cd backend && ./mvnw -B test -Dtest='AuthControllerTest,SecurityConfigTest,ModularityTest'
```

Expected:
- `Tests run: 22, Failures: 0, Errors: 0` (10 test mới, 11 test bảo mật và 1 test ranh giới module).
- Log không còn dòng `Using generated security password`.

- [ ] **Step 11: Commit**

```bash
git add backend/src
git commit -m "feat(identity): add registration, session login/logout and GET /api/me"
```

---

### Task 2: Hồ sơ và đổi mật khẩu

**Files:**
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/common/BusinessException.java`, `.../identity/domain/AccountService.java`, `.../identity/web/MeController.java` (thay toàn bộ)
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/identity/web/MeControllerTest.java`

**Interfaces:**
- Consumes: `AccountService.get(long)`, `TestUsers.loggedIn(...)`, `TestUsers.login(...)` (Task 1).
- Produces:
  - `static BusinessException BusinessException.invalidField(String field, String message)`: tạo lỗi 400 `VALIDATION_FAILED` có `errors: [{field, message}]`, cùng định dạng với lỗi Bean Validation.
  - `AccountService.updateProfile(long id, String fullName, String phone) → User` và `AccountService.changePassword(long id, String currentPassword, String newPassword)`.
  - API: `PUT /api/me` (200, trả `UserResponse`), `PUT /api/me/password` (204).

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/identity/web/MeControllerTest.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

@IntegrationTest
@Transactional
class MeControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void showsCurrentAccountWithRole() throws Exception {
		var staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);

		mvc.perform(get("/api/me").session(staff.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(staff.id()))
			.andExpect(jsonPath("$.email").value(staff.email()))
			.andExpect(jsonPath("$.role").value("STAFF"));
	}

	@Test
	void updatesNameAndPhoneButNeverEmail() throws Exception {
		var user = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);

		mvc.perform(put("/api/me").with(csrf(mvc)).session(user.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName": "Trần Thị B", "phone": "0987654321", "email": "hacker@test.local"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Trần Thị B"))
			.andExpect(jsonPath("$.phone").value("0987654321"))
			.andExpect(jsonPath("$.email").value(user.email()));
	}

	@Test
	void changePasswordNeedsCurrentPassword() throws Exception {
		var user = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);

		mvc.perform(put("/api/me/password").with(csrf(mvc)).session(user.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword": "SaiMatKhau1", "newPassword": "MatKhauMoi9"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

		mvc.perform(put("/api/me/password").with(csrf(mvc)).session(user.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword": "%s", "newPassword": "MatKhauMoi9"}
					""".formatted(TestUsers.PASSWORD)))
			.andExpect(status().isNoContent());
		TestUsers.login(mvc, user.email(), "MatKhauMoi9");
	}

}
```

- [ ] **Step 2: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=MeControllerTest
```

Expected: `Tests run: 3, Failures: 2`. Hai test bị đỏ:
- `updatesNameAndPhoneButNeverEmail`: `Status expected:<200> but was:<405>`, vì chưa có `PUT /api/me`.
- `changePasswordNeedsCurrentPassword`: `Status expected:<400> but was:<404>`, vì chưa có `PUT /api/me/password`.

- [ ] **Step 3: Thêm `invalidField` vào `BusinessException`**

Trong `backend/src/main/java/vn/edu/uit/flightbooking/common/BusinessException.java`, thêm `import java.util.List;` ngay trên `import java.util.Map;`. Sau đó thêm method sau vào ngay trước `public ErrorCode code() {`:

```java
	/** Lỗi của một trường, cùng định dạng {@code errors} với lỗi Bean Validation để form hiện lỗi đúng chỗ. */
	public static BusinessException invalidField(String field, String message) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ",
				Map.of("errors", List.of(Map.of("field", field, "message", message))));
	}
```

`GlobalExceptionHandler.handleBusiness` chép mọi phần tử của `properties` vào ProblemDetail. Vì vậy `errors` xuất hiện trong JSON y như lỗi `@Valid`.

- [ ] **Step 4: Thêm sửa hồ sơ và đổi mật khẩu vào `AccountService`**

Trong `backend/src/main/java/vn/edu/uit/flightbooking/identity/domain/AccountService.java`, thêm hai method sau vào ngay trước `private User create(`:

```java
	/** FR-04: email không đổi được. */
	@Transactional
	public User updateProfile(long id, String fullName, String phone) {
		User user = get(id);
		user.setFullName(fullName);
		user.setPhone(phone);
		return user;
	}

	/** FR-04: phải nhập đúng mật khẩu hiện tại. */
	@Transactional
	public void changePassword(long id, String currentPassword, String newPassword) {
		User user = get(id);
		if (!encoder.matches(currentPassword, user.getPasswordHash())) {
			throw BusinessException.invalidField("currentPassword", "Mật khẩu hiện tại không đúng");
		}
		user.setPasswordHash(encoder.encode(newPassword));
	}
```

Không cần gọi `save`: `user` là entity đang được quản lý, nên Hibernate tự ghi thay đổi khi transaction commit.

- [ ] **Step 5: Thêm API sửa hồ sơ và đổi mật khẩu**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/identity/web/MeController.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.identity.domain.AccountService;

/** Màn hình C-12 (FR-04). */
@RestController
@RequestMapping("/api/me")
class MeController {

	record UpdateProfileRequest(@NotBlank @Size(max = 100) String fullName, @NotBlank @Size(max = 20) String phone) {
	}

	record ChangePasswordRequest(@NotBlank String currentPassword, @Password String newPassword) {
	}

	private final AccountService accounts;

	MeController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	UserResponse me(@AuthenticationPrincipal CurrentUser me) {
		return UserResponse.of(accounts.get(me.id()));
	}

	@PutMapping
	UserResponse updateProfile(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody UpdateProfileRequest body) {
		return UserResponse.of(accounts.updateProfile(me.id(), body.fullName(), body.phone()));
	}

	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody ChangePasswordRequest body) {
		accounts.changePassword(me.id(), body.currentPassword(), body.newPassword());
	}

}
```

`UpdateProfileRequest` không có trường `email`, nên Jackson bỏ qua `email` nếu client gửi lên (FR-04: email không đổi được).

- [ ] **Step 6: Chạy lại test**

```bash
cd backend && ./mvnw -B test -Dtest='MeControllerTest,AuthControllerTest'
```

Expected: `Tests run: 13, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add backend/src
git commit -m "feat(identity): add profile update and password change"
```

---

### Task 3: Quản trị tài khoản và khoá phiên ngay

**Files:**
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/identity/web/AdminUserController.java`, `.../identity/web/LockedAccountInterceptor.java`
- Modify: `.../identity/infra/UserRepository.java` (thay toàn bộ), `.../identity/domain/AccountService.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/identity/web/AdminUserControllerTest.java`

**Interfaces:**
- Consumes: `BusinessException.invalidField` (Task 2), `AccountService.get` (Task 1), `TestUsers` (Task 1).
- Produces:
  - `UserRepository.findStatusById(long) → Optional<UserStatus>` và `UserRepository.search(String pattern, Role role, Pageable) → Page<User>`.
  - `AccountService`:
    - `createOperator(String email, String password, String fullName, String phone, Role role) → User`
    - `isActive(long id) → boolean`
    - `search(String query, Role role, Pageable) → Page<User>`
    - `setStatus(long adminId, long userId, UserStatus status) → User`
  - API: `GET /api/admin/users?q=&role=&page=&size=` (`PagedModel` của `UserResponse`), `POST /api/admin/users` (201), `PATCH /api/admin/users/{id}/status` (200).
  - `LockedAccountInterceptor`: chặn mọi request `/api/**` của tài khoản `LOCKED` (BR-102).

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/identity/web/AdminUserControllerTest.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

@IntegrationTest
@Transactional
class AdminUserControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	ResultActions setStatus(MockHttpSession admin, long userId, String status) throws Exception {
		return mvc.perform(patch("/api/admin/users/{id}/status", userId).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"status": "%s"}
					""".formatted(status)));
	}

	@Test
	void searchesByEmailOrNameAndFiltersByRole() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);
		long staffId = TestUsers.create(jdbc, Role.STAFF, "ops.search@test.local");
		TestUsers.create(jdbc, Role.CUSTOMER, "customer.search@test.local");

		mvc.perform(get("/api/admin/users").param("q", "SEARCH@test").session(admin.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(2));
		mvc.perform(get("/api/admin/users").param("q", "search@").param("role", "STAFF").session(admin.session()))
			.andExpect(jsonPath("$.content[*].id", contains((int) staffId)))
			.andExpect(jsonPath("$.page.totalElements").value(1));
		mvc.perform(get("/api/admin/users").param("q", "search@").param("size", "1").param("sort", "khong_co")
			.session(admin.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	void createsStaffWhoCanLogIn() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(post("/api/admin/users").with(csrf(mvc)).session(admin.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "New.Staff@test.local", "password": "BanDau123", "fullName": "Nhân Viên Mới",
					 "phone": "0911111111", "role": "STAFF"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("new.staff@test.local"))
			.andExpect(jsonPath("$.role").value("STAFF"));

		TestUsers.login(mvc, "new.staff@test.local", "BanDau123");
	}

	@Test
	void cannotCreateCustomerAccounts() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(post("/api/admin/users").with(csrf(mvc)).session(admin.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "c@test.local", "password": "BanDau123", "fullName": "Khách",
					 "phone": "0911111111", "role": "CUSTOMER"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("role"));
	}

	@Test
	void lockingEndsTheUsersOpenSessionOnNextRequest() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);
		var customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);

		setStatus(admin.session(), customer.id(), "LOCKED")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LOCKED"));

		mvc.perform(get("/api/me").session(customer.session()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
		mvc.perform(get("/api/me").session(customer.session()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		setStatus(admin.session(), customer.id(), "ACTIVE").andExpect(status().isOk());
		TestUsers.login(mvc, customer.email(), TestUsers.PASSWORD);
	}

	@Test
	void adminCannotLockThemselves() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		setStatus(admin.session(), admin.id(), "LOCKED")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

	@Test
	void unknownUserIsNotFound() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		setStatus(admin.session(), Long.MAX_VALUE, "LOCKED")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

}
```

- [ ] **Step 2: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=AdminUserControllerTest
```

Expected: `Tests run: 6, Failures: 5`; mọi test đỏ đều nhận `404` vì chưa có route. Riêng `unknownUserIsNotFound` đã xanh, vì route chưa tồn tại cũng trả 404 `RESOURCE_NOT_FOUND`. Sau Step 5, test này mới thật sự kiểm tra trường hợp id không tồn tại.

- [ ] **Step 3: Thêm truy vấn vào `UserRepository`**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/identity/infra/UserRepository.java`:

```java
package vn.edu.uit.flightbooking.identity.infra;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.identity.domain.User;
import vn.edu.uit.flightbooking.identity.domain.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	@Query("SELECT u.status FROM User u WHERE u.id = :id")
	Optional<UserStatus> findStatusById(long id);

	/** FR-110: {@code pattern} dạng {@code %chuỗi thường%}, khớp email hoặc họ tên; {@code role} null là mọi vai trò. */
	@Query("""
			SELECT u FROM User u
			WHERE (:role IS NULL OR u.role = :role)
			  AND (lower(u.email) LIKE :pattern OR lower(u.fullName) LIKE :pattern)
			ORDER BY u.id DESC
			""")
	Page<User> search(String pattern, Role role, Pageable pageable);

}
```

- [ ] **Step 4: Thêm các thao tác quản trị vào `AccountService`**

Trong `backend/src/main/java/vn/edu/uit/flightbooking/identity/domain/AccountService.java`:

1. Thêm ba import ngay dưới `import org.springframework.dao.DataIntegrityViolationException;`:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
```

2. Thêm method sau ngay **sau** method `register(...)`:

```java
	/** FR-110, BR-101: Admin chỉ tạo tài khoản vận hành. */
	@Transactional
	public User createOperator(String email, String password, String fullName, String phone, Role role) {
		if (role == Role.CUSTOMER) {
			throw BusinessException.invalidField("role", "Chỉ tạo được tài khoản STAFF hoặc ADMIN");
		}
		return create(email, password, fullName, phone, role);
	}
```

3. Thêm method sau ngay **sau** method `get(long id)`:

```java
	@Transactional(readOnly = true)
	public boolean isActive(long id) {
		return users.findStatusById(id).filter(status -> status == UserStatus.ACTIVE).isPresent();
	}
```

4. Thêm hai method sau ngay **trước** `private User create(`:

```java
	/** FR-110. Bỏ sort client gửi lên: truy vấn đã có ORDER BY, sort lạ sẽ làm hỏng câu JPQL. */
	@Transactional(readOnly = true)
	public Page<User> search(String query, Role role, Pageable pageable) {
		String pattern = "%" + query.strip().toLowerCase(Locale.ROOT) + "%";
		return users.search(pattern, role, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
	}

	/** FR-110, BR-105: Admin không tự khoá chính mình. Khoá có hiệu lực từ request kế tiếp (BR-102). */
	@Transactional
	public User setStatus(long adminId, long userId, UserStatus status) {
		if (adminId == userId && status == UserStatus.LOCKED) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Bạn không thể tự khoá tài khoản của chính mình");
		}
		User user = get(userId);
		user.setStatus(status);
		return user;
	}
```

- [ ] **Step 5: Viết controller quản trị và interceptor khoá phiên**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/web/AdminUserController.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.identity.domain.AccountService;
import vn.edu.uit.flightbooking.identity.domain.UserStatus;

/** Màn hình A-08 (FR-110). */
@RestController
@RequestMapping("/api/admin/users")
class AdminUserController {

	record CreateUserRequest(@NotBlank @Email @Size(max = 255) String email, @Password String password,
			@NotBlank @Size(max = 100) String fullName, @NotBlank @Size(max = 20) String phone, @NotNull Role role) {
	}

	record UpdateStatusRequest(@NotNull UserStatus status) {
	}

	private final AccountService accounts;

	AdminUserController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	PagedModel<UserResponse> search(@RequestParam(defaultValue = "") String q,
			@RequestParam(required = false) Role role, @PageableDefault(size = 20) Pageable pageable) {
		return new PagedModel<>(accounts.search(q, role, pageable).map(UserResponse::of));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse create(@Valid @RequestBody CreateUserRequest body) {
		return UserResponse.of(accounts.createOperator(body.email(), body.password(), body.fullName(), body.phone(),
				body.role()));
	}

	@PatchMapping("/{id}/status")
	UserResponse updateStatus(@PathVariable long id, @Valid @RequestBody UpdateStatusRequest body,
			@AuthenticationPrincipal CurrentUser me) {
		return UserResponse.of(accounts.setStatus(me.id(), id, body.status()));
	}

}
```

`new PagedModel<>(page)` cho ra đúng định dạng phân trang của TDD §5.3: `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`. Các API phân trang ở plan sau làm theo cùng mẫu này.

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/web/LockedAccountInterceptor.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.identity.domain.AccountService;

/**
 * BR-102: phiên của tài khoản bị khoá mất hiệu lực ngay ở request kế tiếp (TDD §4.1). Mỗi request đã đăng nhập
 * đọc trạng thái user theo khoá chính. Dùng interceptor của Spring MVC thay cho filter của Spring Security vì
 * SecurityConfig nằm ở common và common không được phụ thuộc identity; lỗi ném ra ở đây đi qua
 * GlobalExceptionHandler nên vẫn là ProblemDetail.
 */
@Component
class LockedAccountInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final AccountService accounts;

	LockedAccountInterceptor(AccountService accounts) {
		this.accounts = accounts;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns("/api/**");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user
				&& !accounts.isActive(user.id())) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			SecurityContextHolder.clearContext();
			throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Tài khoản đã bị khoá");
		}
		return true;
	}

}
```

Ghi chú:
- **Principal khác `CurrentUser` được bỏ qua.** Các test dùng `@WithMockUser` của Plan 01 có principal là `User` của Spring Security, nên interceptor không đụng tới chúng.
- **Một class làm hai việc.** Interceptor tự đăng ký mình qua `WebMvcConfigurer`, nên không cần thêm một class cấu hình riêng.

- [ ] **Step 6: Chạy lại test**

```bash
cd backend && ./mvnw -B test -Dtest='AdminUserControllerTest,AuthControllerTest,MeControllerTest,SecurityConfigTest,ModularityTest'
```

Expected: `Tests run: 31, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add backend/src
git commit -m "feat(identity): add admin account management and end sessions of locked accounts"
```

---

### Task 4: Quản trị tham số hệ thống

**Files:**
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/common/web/AdminSettingsController.java`
- Modify: `.../common/SettingKey.java`, `.../common/SettingsApi.java` (thay toàn bộ)
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/common/web/AdminSettingsControllerTest.java`

**Interfaces:**
- Consumes: `CurrentUser` (Task 1), `TestUsers` (Task 1), `SettingKey` và `SettingsApi.getInt` (Plan 01).
- Produces:
  - `static SettingKey SettingKey.of(String key)`: khoá lạ thì báo 404 `RESOURCE_NOT_FOUND`.
  - `record SettingsApi.Setting(String key, int value, String description, int min, int max, Instant updatedAt, String updatedByName)`.
  - `SettingsApi.list() → List<Setting>` và `SettingsApi.update(SettingKey key, int value, long updatedBy)`.
  - API: `GET /api/admin/settings` (200), `PUT /api/admin/settings/{key}` với body `{ "value": n }` (204).

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/common/web/AdminSettingsControllerTest.java`:

```java
package vn.edu.uit.flightbooking.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;

@IntegrationTest
@Transactional
class AdminSettingsControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	SettingsApi settings;

	ResultActions update(MockHttpSession admin, String key, String body) throws Exception {
		return mvc.perform(put("/api/admin/settings/{key}", key).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	@Test
	void listsAllSettingsWithValidRange() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(get("/api/admin/settings").session(admin.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(8))
			.andExpect(jsonPath("$[?(@.key == 'booking.hold_minutes')].value").value(15))
			.andExpect(jsonPath("$[?(@.key == 'booking.hold_minutes')].min").value(5))
			.andExpect(jsonPath("$[?(@.key == 'booking.hold_minutes')].max").value(60));
	}

	@Test
	void updateRecordsWhoChangedItAndAppliesToNextRead() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		update(admin.session(), "booking.hold_minutes", """
				{"value": 20}
				""").andExpect(status().isNoContent());

		assertThat(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES)).isEqualTo(20);
		mvc.perform(get("/api/admin/settings").session(admin.session()))
			.andExpect(jsonPath("$[?(@.key == 'booking.hold_minutes')].updatedByName").value("Người Dùng Test"));
	}

	@Test
	void valueOutsideRangeIsRejected() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		update(admin.session(), "booking.hold_minutes", """
				{"value": 61}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SETTING_OUT_OF_RANGE"));
		assertThat(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES)).isEqualTo(15);
	}

	@Test
	void minConnectionMustStayBelowMax() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		// 600 nằm trong khoảng của min (30–600) nhưng không nhỏ hơn max hiện tại sau khi max = 600.
		update(admin.session(), "search.max_connection_minutes", """
				{"value": 600}
				""").andExpect(status().isNoContent());
		update(admin.session(), "search.min_connection_minutes", """
				{"value": 600}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SETTING_OUT_OF_RANGE"));
		update(admin.session(), "search.max_connection_minutes", """
				{"value": 60}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SETTING_OUT_OF_RANGE"));
	}

	@Test
	void missingValueIsValidationErrorNotZero() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		update(admin.session(), "pricing.child_percent", "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		assertThat(settings.getInt(SettingKey.PRICING_CHILD_PERCENT)).isEqualTo(90);
	}

	@Test
	void unknownKeyIsNotFound() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		update(admin.session(), "khong.ton_tai", """
				{"value": 1}
				""")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

}
```

- [ ] **Step 2: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=AdminSettingsControllerTest
```

Expected: `Tests run: 6, Failures: 5`; mọi test đỏ đều nhận `404`. Riêng `unknownKeyIsNotFound` đã xanh, vì route chưa có cũng trả 404 `RESOURCE_NOT_FOUND`.

- [ ] **Step 3: Thêm hàm tra khoá vào `SettingKey`**

Trong `backend/src/main/java/vn/edu/uit/flightbooking/common/SettingKey.java`, thêm `import java.util.Arrays;` ngay dưới dòng `package` (cách một dòng trống). Sau đó thêm method sau vào ngay trước `public String key() {`:

```java
	/** Tìm theo khoá trong DB (VD {@code booking.hold_minutes}); khoá lạ thì báo RESOURCE_NOT_FOUND. */
	public static SettingKey of(String key) {
		return Arrays.stream(values())
			.filter(k -> k.key.equals(key))
			.findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không có tham số " + key));
	}
```

- [ ] **Step 4: Thêm đọc danh sách và sửa tham số vào `SettingsApi`**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/common/SettingsApi.java`:

```java
package vn.edu.uit.flightbooking.common;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc tham số hệ thống. Module khác gọi {@link #getInt} ngay lúc tạo giao dịch,
 * nên giá trị mới chỉ áp dụng cho giao dịch tạo sau đó (BR-25).
 */
@Service
public class SettingsApi {

	/** Một dòng của màn hình A-09; {@code updatedByName} là null khi chưa ai sửa. */
	public record Setting(String key, int value, String description, int min, int max,
			Instant updatedAt, String updatedByName) {
	}

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

	/** FR-111. Chỉ đọc tên người sửa từ bảng users, không ghi. */
	public List<Setting> list() {
		return jdbc.sql("""
				SELECT s.key, s.value, s.description, s.updated_at, u.full_name
				FROM system_settings s LEFT JOIN users u ON u.id = s.updated_by
				ORDER BY s.key
				""")
			.query((rs, rowNum) -> {
				SettingKey key = SettingKey.of(rs.getString("key"));
				return new Setting(key.key(), rs.getInt("value"), rs.getString("description"), key.min(), key.max(),
						rs.getTimestamp("updated_at").toInstant(), rs.getString("full_name"));
			})
			.list();
	}

	/** FR-111: kiểm tra khoảng hợp lệ (PRD §7), ghi người sửa và thời điểm sửa. */
	@Transactional
	public void update(SettingKey key, int value, long updatedBy) {
		if (value < key.min() || value > key.max()) {
			throw new BusinessException(ErrorCode.SETTING_OUT_OF_RANGE,
					"Giá trị phải nằm trong khoảng %d–%d".formatted(key.min(), key.max()));
		}
		if (key == SettingKey.SEARCH_MIN_CONNECTION_MINUTES || key == SettingKey.SEARCH_MAX_CONNECTION_MINUTES) {
			// Luôn khoá dòng min rồi tới dòng max, để hai Admin sửa cùng lúc không phá điều kiện min < max.
			int min = lockedValue(SettingKey.SEARCH_MIN_CONNECTION_MINUTES);
			int max = lockedValue(SettingKey.SEARCH_MAX_CONNECTION_MINUTES);
			if (key == SettingKey.SEARCH_MIN_CONNECTION_MINUTES) {
				min = value;
			}
			else {
				max = value;
			}
			if (min >= max) {
				throw new BusinessException(ErrorCode.SETTING_OUT_OF_RANGE,
						"Thời gian nối chuyến tối thiểu phải nhỏ hơn thời gian tối đa");
			}
		}
		jdbc.sql("UPDATE system_settings SET value = ?, updated_at = now(), updated_by = ? WHERE key = ?")
			.params(String.valueOf(value), updatedBy, key.key())
			.update();
	}

	private int lockedValue(SettingKey key) {
		return jdbc.sql("SELECT value FROM system_settings WHERE key = ? FOR UPDATE")
			.param(key.key())
			.query(Integer.class)
			.single();
	}

}
```

Ghi chú:
- **Vì sao `common` đọc bảng `users`.** `list()` JOIN sang `users` để lấy tên người sửa, giống cách module `report` đọc mọi bảng bằng SQL. Quy tắc sở hữu bảng chỉ áp dụng cho thao tác **ghi**, và ở đây không có phụ thuộc Java nào sang `identity`.
- **Vì sao khoá hai dòng.** Nếu khoá theo thứ tự tuỳ ý, hai Admin cùng lúc sửa min và max có thể deadlock. Nếu không khoá, cả hai có thể cùng lọt qua kiểm tra và để lại min ≥ max.

- [ ] **Step 5: Viết controller**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/common/web/AdminSettingsController.java`:

```java
package vn.edu.uit.flightbooking.common.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;

/** Màn hình A-09 (FR-111, TDD §6.9). */
@RestController
@RequestMapping("/api/admin/settings")
class AdminSettingsController {

	/** Thiếu {@code value} thì Jackson 3 báo lỗi (FAIL_ON_NULL_FOR_PRIMITIVES bật sẵn), không hiểu thành 0. */
	record UpdateSettingRequest(int value) {
	}

	private final SettingsApi settings;

	AdminSettingsController(SettingsApi settings) {
		this.settings = settings;
	}

	@GetMapping
	List<SettingsApi.Setting> list() {
		return settings.list();
	}

	@PutMapping("/{key}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void update(@PathVariable String key, @RequestBody UpdateSettingRequest body,
			@AuthenticationPrincipal CurrentUser me) {
		settings.update(SettingKey.of(key), body.value(), me.id());
	}

}
```

Đường dẫn `/api/admin/settings/booking.hold_minutes` có dấu chấm, và `{key}` nhận đủ cả chuỗi. Spring 7 không còn cắt phần sau dấu chấm như kiểu đuôi file.

- [ ] **Step 6: Chạy lại test**

```bash
cd backend && ./mvnw -B test -Dtest='AdminSettingsControllerTest,SettingsApiTest,SecurityConfigTest'
```

Expected: `Tests run: 20, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add backend/src
git commit -m "feat(common): add admin API to list and update system settings"
```

---

### Task 5: Quên và đặt lại mật khẩu

**Files:**
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/identity/PasswordResetRequested.java`, `.../identity/domain/PasswordResetService.java`
- Modify: `.../identity/domain/AccountService.java`, `.../identity/web/AuthController.java` (thay toàn bộ)
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/identity/web/PasswordResetTest.java`

**Interfaces:**
- Consumes: `AccountService.get` (Task 1), `TestUsers` (Task 1).
- Produces:
  - Sự kiện công khai `record identity.PasswordResetRequested(long userId, String rawToken)`. `toString()` của record giấu token.
  - `PasswordResetService.request(String email)` và `PasswordResetService.reset(String token, String newPassword)`.
  - `AccountService.findByEmail(String) → Optional<User>` và `AccountService.setPassword(long id, String newPassword)`.
  - API: `POST /api/auth/forgot-password` `{ email }` (luôn 200) và `POST /api/auth/reset-password` `{ token, newPassword }` (204).

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/identity/web/PasswordResetTest.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.identity.PasswordResetRequested;

/**
 * Lấy token từ sự kiện {@link PasswordResetRequested}. Test chạy trong transaction rollback nên email không được
 * gửi (listener chỉ chạy sau commit); luồng có email nằm ở {@code PasswordResetEmailTest}.
 */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class PasswordResetTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	ResultActions forgot(String email) throws Exception {
		return mvc.perform(post("/api/auth/forgot-password").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s"}
					""".formatted(email)));
	}

	ResultActions reset(String token, String newPassword) throws Exception {
		return mvc.perform(post("/api/auth/reset-password").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"token": "%s", "newPassword": "%s"}
					""".formatted(token, newPassword)));
	}

	String requestToken(String email) throws Exception {
		forgot(email).andExpect(status().isOk());
		return events.stream(PasswordResetRequested.class).reduce((first, second) -> second).orElseThrow().rawToken();
	}

	@Test
	void unknownEmailStillGetsOkAndNoToken() throws Exception {
		forgot("nobody@test.local").andExpect(status().isOk());

		assertThat(events.stream(PasswordResetRequested.class)).isEmpty();
	}

	@Test
	void tokenIsStoredOnlyAsHashAndExpiresIn30Minutes() throws Exception {
		long id = TestUsers.create(jdbc, Role.CUSTOMER, "reset@test.local");

		String token = requestToken("reset@test.local");

		var row = jdbc.sql("""
				SELECT token_hash, extract(epoch FROM expires_at - created_at) AS ttl
				FROM password_reset_tokens WHERE user_id = ?
				""").param(id).query().singleRow();
		assertThat(row.get("token_hash")).isNotEqualTo(token).asString().hasSize(64);
		assertThat(((Number) row.get("ttl")).intValue()).isEqualTo(30 * 60);
	}

	@Test
	void resetChangesPasswordOnce() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "reset@test.local");
		String token = requestToken("RESET@test.local");

		reset(token, "MatKhauMoi9").andExpect(status().isNoContent());
		TestUsers.login(mvc, "reset@test.local", "MatKhauMoi9");

		reset(token, "MatKhauKhac9")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		long id = TestUsers.create(jdbc, Role.CUSTOMER, "reset@test.local");
		String token = requestToken("reset@test.local");
		jdbc.sql("UPDATE password_reset_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?")
			.param(id)
			.update();

		reset(token, "MatKhauMoi9")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void madeUpTokenIsRejected() throws Exception {
		reset("khong-phai-token-that", "MatKhauMoi9")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void newPasswordMustFollowPasswordRule() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "reset@test.local");
		String token = requestToken("reset@test.local");

		reset(token, "ngan1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("newPassword"));
		// Token chưa bị tiêu thụ bởi request không hợp lệ.
		reset(token, "MatKhauMoi9").andExpect(status().isNoContent());
	}

}
```

Ghi chú:
- **`created_at` và `expires_at` cùng dùng `now()`** trong một transaction, nên hiệu của chúng đúng bằng 1800 giây.
- **`@RecordApplicationEvents` không tạo Spring context mới.** Annotation này chỉ bật một test listener, nên context dùng chung vẫn được giữ.

- [ ] **Step 2: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=PasswordResetTest
```

Expected: `BUILD FAILURE` do lỗi biên dịch `cannot find symbol` ở `PasswordResetTest.java`, vì chưa có `vn.edu.uit.flightbooking.identity.PasswordResetRequested`.

- [ ] **Step 3: Viết sự kiện và service đặt lại mật khẩu**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/PasswordResetRequested.java`:

```java
package vn.edu.uit.flightbooking.identity;

/**
 * Phát khi có yêu cầu đặt lại mật khẩu (TDD §3.3); notification gửi email chứa {@code rawToken}.
 * {@link #toString()} giấu token để token không lọt vào log (NFR-03).
 */
public record PasswordResetRequested(long userId, String rawToken) {

	@Override
	public String toString() {
		return "PasswordResetRequested[userId=" + userId + "]";
	}

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/domain/PasswordResetService.java`:

```java
package vn.edu.uit.flightbooking.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.identity.PasswordResetRequested;

/** FR-03, BR-103, TDD §4.4. DB chỉ lưu SHA-256 của token. */
@Service
public class PasswordResetService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AccountService accounts;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	PasswordResetService(AccountService accounts, JdbcClient jdbc, ApplicationEventPublisher events) {
		this.accounts = accounts;
		this.jdbc = jdbc;
		this.events = events;
	}

	/** Email không tồn tại thì im lặng bỏ qua: API luôn trả 200 để không lộ email nào đã đăng ký. */
	@Transactional
	public void request(String email) {
		accounts.findByEmail(email).ifPresent(user -> {
			byte[] bytes = new byte[32];
			RANDOM.nextBytes(bytes);
			String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
			jdbc.sql("""
					INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
					VALUES (?, ?, now() + interval '30 minutes')
					""")
				.params(user.getId(), sha256(token))
				.update();
			events.publishEvent(new PasswordResetRequested(user.getId(), token));
		});
	}

	@Transactional
	public void reset(String token, String newPassword) {
		// Đánh dấu đã dùng bằng một câu UPDATE nguyên tử: hai request cùng token thì chỉ một request thành công.
		long userId = jdbc.sql("""
				UPDATE password_reset_tokens SET used_at = now()
				WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
				RETURNING user_id
				""")
			.param(sha256(token))
			.query(Long.class)
			.optional()
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN,
					"Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));
		accounts.setPassword(userId, newPassword);
	}

	private static String sha256(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
```

Ghi chú:
- **Bảng `password_reset_tokens` dùng `JdbcClient`, không có entity.** Bảng chỉ cần hai câu SQL. Câu tiêu thụ token phải là một `UPDATE ... RETURNING` nguyên tử, giống cách Q-01 giữ ghế.
- **Hết hạn do DB tính.** `expires_at` được tính bằng `now()` của DB, nên đồng hồ của backend lệch cũng không ảnh hưởng.

- [ ] **Step 4: Thêm `findByEmail` và `setPassword` vào `AccountService`**

Trong `backend/src/main/java/vn/edu/uit/flightbooking/identity/domain/AccountService.java`:

1. Thêm `import java.util.Optional;` ngay dưới `import java.util.Locale;`.
2. Thêm method sau ngay **trước** method `isActive(long id)`:

```java
	@Transactional(readOnly = true)
	public Optional<User> findByEmail(String email) {
		return users.findByEmail(normalize(email));
	}
```

3. Thêm method sau ngay **sau** method `changePassword(...)`:

```java
	/** Dùng sau khi token đặt lại mật khẩu đã được kiểm tra (FR-03). */
	@Transactional
	public void setPassword(long id, String newPassword) {
		get(id).setPasswordHash(encoder.encode(newPassword));
	}
```

- [ ] **Step 5: Thêm hai API vào `AuthController`**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/identity/web/AuthController.java`:

```java
package vn.edu.uit.flightbooking.identity.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.identity.domain.AccountService;
import vn.edu.uit.flightbooking.identity.domain.PasswordResetService;
import vn.edu.uit.flightbooking.identity.domain.User;

/** Đăng ký, đăng nhập, đăng xuất, quên mật khẩu (FR-01–03, TDD §4.1, §4.4). */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	record RegisterRequest(@NotBlank @Email @Size(max = 255) String email, @Password String password,
			@NotBlank @Size(max = 100) String fullName, @NotBlank @Size(max = 20) String phone) {
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	record ForgotPasswordRequest(@NotBlank @Email String email) {
	}

	record ResetPasswordRequest(@NotBlank String token, @Password String newPassword) {
	}

	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	private final AccountService accounts;

	private final PasswordResetService passwordResets;

	AuthController(AccountService accounts, PasswordResetService passwordResets) {
		this.accounts = accounts;
		this.passwordResets = passwordResets;
	}

	/** FR-01: đăng ký xong thì đăng nhập luôn. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User user = accounts.register(body.email(), body.password(), body.fullName(), body.phone());
		signIn(user, request, response);
		return UserResponse.of(user);
	}

	@PostMapping("/login")
	UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User user = accounts.authenticate(body.email(), body.password());
		signIn(user, request, response);
		return UserResponse.of(user);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

	/** FR-03: luôn trả 200, kể cả khi email không tồn tại. */
	@PostMapping("/forgot-password")
	void forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
		passwordResets.request(body.email());
	}

	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
		passwordResets.reset(body.token(), body.newPassword());
	}

	/** TDD §4.1: đổi session id (chống session fixation) rồi lưu SecurityContext vào HttpSession. */
	private void signIn(User user, HttpServletRequest request, HttpServletResponse response) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		var principal = new CurrentUser(user.getId(), user.getRole());
		var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}

}
```

So với Task 1, file này có thêm hai record request, trường `passwordResets` (cả trong constructor) và hai method `forgotPassword`, `resetPassword`.

- [ ] **Step 6: Chạy lại test**

```bash
cd backend && ./mvnw -B test -Dtest='PasswordResetTest,AuthControllerTest,ModularityTest'
```

Expected: `Tests run: 17, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add backend/src
git commit -m "feat(identity): add forgot/reset password with single-use hashed tokens"
```

---

### Task 6: Khung email và email đặt lại mật khẩu

**Files:**
- Create:
  - `backend/src/main/java/vn/edu/uit/flightbooking/identity/UserApi.java`
  - `.../identity/UserSummary.java`
  - `.../notification/infra/Mailer.java`
  - `.../notification/domain/PasswordResetEmail.java`
  - `backend/src/main/resources/templates/email/password-reset.html`
- Modify:
  - `backend/pom.xml`
  - `.../FlightBookingApplication.java` (thay toàn bộ)
  - `backend/src/main/resources/application.properties`
  - `backend/src/test/java/vn/edu/uit/flightbooking/IntegrationTest.java`
  - `.env.example`
  - `docker-compose.yml`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/TestMailSender.java`, `backend/src/test/java/vn/edu/uit/flightbooking/notification/PasswordResetEmailTest.java`

**Interfaces:**
- Consumes: `PasswordResetRequested` (Task 5), `AccountService.get` (Task 1), `TestUsers` (Task 1).
- Produces:
  - API công khai `identity.UserApi.get(long id) → UserSummary`, với `record identity.UserSummary(long id, String email, String fullName)`. Không có tài khoản thì báo 404.
  - Bean `notification.infra.Mailer` có method `send(String to, String subject, String template, Map<String, Object> variables)`. Method này render `templates/email/<template>.html` và gửi email HTML; gửi lỗi thì chỉ ghi log `ERROR`.
  - `@EnableAsync` trên `FlightBookingApplication`, để mọi `@ApplicationModuleListener` chạy bất đồng bộ.
  - Cấu hình `app.base-url` (biến `APP_BASE_URL`) và `app.mail-from` (biến `MAIL_FROM`).
  - Test: bean `TestMailSender` có `sentTo(String email) → List<MimeMessage>`, đã có sẵn trong mọi `@IntegrationTest`.

- [ ] **Step 1: Viết test helper `TestMailSender` và gắn vào `@IntegrationTest`**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/TestMailSender.java`:

```java
package vn.edu.uit.flightbooking;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Thay JavaMailSender thật trong test: giữ email lại trong bộ nhớ để test đọc, không gửi đi đâu.
 * Có bean này thì Spring Boot không tự tạo JavaMailSender từ {@code spring.mail.*}.
 */
public class TestMailSender extends JavaMailSenderImpl {

	private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();

	@Override
	protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
		sent.addAll(List.of(mimeMessages));
	}

	public List<MimeMessage> sentTo(String email) {
		return sent.stream().filter(message -> isRecipient(message, email)).toList();
	}

	private static boolean isRecipient(MimeMessage message, String email) {
		try {
			return Arrays.stream(message.getAllRecipients()).anyMatch(address -> address.toString().equals(email));
		}
		catch (MessagingException e) {
			throw new IllegalStateException(e);
		}
	}

}
```

Trong `backend/src/test/java/vn/edu/uit/flightbooking/IntegrationTest.java`, sửa câu cuối của Javadoc và dòng `@Import`:

```java
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
```

Ghi chú:
- **`@Import` một class thường cũng đăng ký nó thành bean.** Mọi test đều dùng cùng annotation này, nên vẫn chỉ có một Spring context dùng chung.
- **Test không cần Mailpit.** Khi đã có bean `JavaMailSender`, Spring Boot bỏ qua cấu hình `spring.mail.*`.

- [ ] **Step 2: Viết test email đặt lại mật khẩu**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/notification/PasswordResetEmailTest.java`:

```java
package vn.edu.uit.flightbooking.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestMailSender;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

/**
 * Không dùng {@code @Transactional}: email chỉ gửi sau khi transaction commit, nên test này commit thật
 * và tự dọn dữ liệu ở {@link #cleanUp()}.
 */
@IntegrationTest
class PasswordResetEmailTest {

	private static final Pattern TOKEN = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailSender mail;

	final String email = TestUsers.randomEmail();

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email = ?)")
			.param(email)
			.update();
		jdbc.sql("DELETE FROM users WHERE email = ?").param(email).update();
	}

	@Test
	void emailLinkResetsPassword() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, email);

		mvc.perform(post("/api/auth/forgot-password").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s"}
					""".formatted(email)))
			.andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).until(() -> !mail.sentTo(email).isEmpty());
		MimeMessage message = mail.sentTo(email).getFirst();
		String html = (String) message.getContent();
		assertThat(message.getSubject()).isEqualTo("Đặt lại mật khẩu SkyLine");
		assertThat(html).contains("http://localhost:3000/reset-password?token=").contains("30 phút");

		Matcher link = TOKEN.matcher(html);
		assertThat(link.find()).isTrue();
		mvc.perform(post("/api/auth/reset-password").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"token": "%s", "newPassword": "QuaEmail123"}
					""".formatted(link.group(1))))
			.andExpect(status().isNoContent());
		TestUsers.login(mvc, email, "QuaEmail123");
	}

}
```

Awaitility đã có sẵn trong classpath test, vì `spring-modulith-starter-test` kéo theo.

- [ ] **Step 3: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=PasswordResetEmailTest
```

Expected: `Tests run: 1, Failures: 0, Errors: 1`. Lỗi là `ConditionTimeoutException: ... was not fulfilled within 10 seconds`, vì chưa có ai gửi email. Test này mất khoảng 10 giây chờ.

- [ ] **Step 4: Thêm dependency và bật xử lý bất đồng bộ**

Trong `backend/pom.xml`, thêm dependency sau ngay sau khối `spring-modulith-starter-core`. Phiên bản lấy từ `spring-modulith-bom` nên không cần ghi:

```xml
		<dependency>
			<groupId>org.springframework.modulith</groupId>
			<artifactId>spring-modulith-events-api</artifactId>
		</dependency>
```

`@ApplicationModuleListener` nằm trong artifact này; `spring-modulith-starter-core` không kéo theo nó. Artifact chỉ chứa annotation, không bật Event Publication Registry (TDD §6.10 ghi rõ việc đó nằm ngoài phạm vi).

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/FlightBookingApplication.java`:

```java
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
```

- [ ] **Step 5: Cấu hình mail và địa chỉ frontend**

Thêm vào cuối `backend/src/main/resources/application.properties`:

```properties

spring.mail.host=${MAIL_HOST:localhost}
spring.mail.port=${MAIL_PORT:1025}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
# Gửi email lỗi chỉ ghi log (FR-130), nên SMTP tắt không được làm /actuator/health báo DOWN.
management.health.mail.enabled=false
app.mail-from=${MAIL_FROM:no-reply@flightbooking.local}
app.base-url=${APP_BASE_URL:http://localhost:3000}
```

Ghi chú:
- **Phải tắt health check của mail.** Nếu không, Mailpit dừng thì `/actuator/health` báo `DOWN`, healthcheck của Docker hỏng, và test `healthIsPublic` của Plan 01 cũng hỏng.
- **Các giá trị mặc định khớp Mailpit khi dev,** nên file `.env` cũ chưa có `MAIL_*` vẫn chạy được.

Thêm vào cuối `.env.example`:

```properties

# SMTP. Khi dev dùng Mailpit (không cần tài khoản), xem email ở http://localhost:8025.
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=no-reply@flightbooking.local

# Địa chỉ frontend, dùng cho link trong email (VD link đặt lại mật khẩu).
APP_BASE_URL=http://localhost:3000
```

Trong `docker-compose.yml`, ở service `backend`, thêm `MAIL_HOST: mailpit` vào `environment` và thêm `mailpit` vào `depends_on`:

```yaml
  backend:
    build: ./backend
    env_file: .env
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/flightbooking
      MAIL_HOST: mailpit
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      mailpit:
        condition: service_started
```

Phần `healthcheck` của `backend` giữ nguyên.

- [ ] **Step 6: Viết API công khai `UserApi` của identity**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/UserSummary.java`:

```java
package vn.edu.uit.flightbooking.identity;

/** Thông tin tóm tắt của một tài khoản cho module khác (VD notification lấy email người nhận). */
public record UserSummary(long id, String email, String fullName) {
}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/identity/UserApi.java`:

```java
package vn.edu.uit.flightbooking.identity;

import org.springframework.stereotype.Service;

import vn.edu.uit.flightbooking.identity.domain.AccountService;
import vn.edu.uit.flightbooking.identity.domain.User;

/** API công khai của identity (TDD §3.2). */
@Service
public class UserApi {

	private final AccountService accounts;

	UserApi(AccountService accounts) {
		this.accounts = accounts;
	}

	/** Không có tài khoản thì báo RESOURCE_NOT_FOUND. */
	public UserSummary get(long id) {
		User user = accounts.get(id);
		return new UserSummary(user.getId(), user.getEmail(), user.getFullName());
	}

}
```

- [ ] **Step 7: Viết khung gửi email, listener và template**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/notification/infra/Mailer.java`:

```java
package vn.edu.uit.flightbooking.notification.infra;

import java.util.Locale;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Render template {@code templates/email/<tên>.html} rồi gửi email HTML (TDD §6.10).
 * Gửi lỗi thì chỉ ghi log ERROR, không ném lỗi và không tự gửi lại (FR-130).
 */
@Component
public class Mailer {

	private static final Logger log = LoggerFactory.getLogger(Mailer.class);

	private static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

	private final JavaMailSender sender;

	private final ITemplateEngine templates;

	private final String from;

	Mailer(JavaMailSender sender, ITemplateEngine templates, @Value("${app.mail-from}") String from) {
		this.sender = sender;
		this.templates = templates;
		this.from = from;
	}

	public void send(String to, String subject, String template, Map<String, Object> variables) {
		String html = templates.process("email/" + template, new Context(VIETNAMESE, variables));
		try {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(html, true);
			sender.send(message);
		}
		catch (MailException | MessagingException e) {
			log.error("Gửi email '{}' thất bại", template, e);
		}
	}

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/notification/domain/PasswordResetEmail.java`:

```java
package vn.edu.uit.flightbooking.notification.domain;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.flightbooking.identity.PasswordResetRequested;
import vn.edu.uit.flightbooking.identity.UserApi;
import vn.edu.uit.flightbooking.identity.UserSummary;
import vn.edu.uit.flightbooking.notification.infra.Mailer;

/** Email đặt lại mật khẩu (FR-03, FR-130). Chạy bất đồng bộ, sau khi transaction của identity commit. */
@Component
class PasswordResetEmail {

	private final UserApi users;

	private final Mailer mailer;

	private final String baseUrl;

	PasswordResetEmail(UserApi users, Mailer mailer, @Value("${app.base-url}") String baseUrl) {
		this.users = users;
		this.mailer = mailer;
		this.baseUrl = baseUrl;
	}

	@ApplicationModuleListener
	void on(PasswordResetRequested event) {
		UserSummary user = users.get(event.userId());
		mailer.send(user.email(), "Đặt lại mật khẩu SkyLine", "password-reset", Map.of(
				"fullName", user.fullName(),
				"link", baseUrl + "/reset-password?token=" + event.rawToken()));
	}

}
```

Tạo `backend/src/main/resources/templates/email/password-reset.html`. Template dùng màu Deep Ink, Pure White, Cool Ash và nút bo tròn của DESIGN.md:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Đặt lại mật khẩu SkyLine</title>
</head>
<body style="margin:0;padding:24px;background:#ffffff;color:#000d10;font-family:Inter,Arial,sans-serif;font-size:16px;line-height:1.6;">
  <p>Xin chào <span th:text="${fullName}">Nguyễn Văn A</span>,</p>
  <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản SkyLine của bạn. Bấm nút dưới đây để đặt mật khẩu mới.</p>
  <p>
    <a th:href="${link}" href="#"
       style="display:inline-block;padding:12px 24px;border-radius:1000px;background:#000d10;color:#ffffff;text-decoration:none;font-weight:700;">Đặt lại mật khẩu</a>
  </p>
  <p style="color:#8e8e95;">Link chỉ dùng được một lần và hết hạn sau 30 phút. Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.</p>
</body>
</html>
```

Ghi chú cho các plan sau:
- **Thêm một email mới** gồm hai việc: một template `templates/email/<tên>.html` và một method `@ApplicationModuleListener` trong `notification.domain` gọi `mailer.send(...)`.
- **Style viết inline** vì nhiều trình đọc email bỏ qua thẻ `<style>`.

- [ ] **Step 8: Chạy lại test**

```bash
cd backend && ./mvnw -B test -Dtest='PasswordResetEmailTest,PasswordResetTest,SecurityConfigTest,ModularityTest'
```

Expected: `Tests run: 19, Failures: 0, Errors: 0`. `ModularityTest` xanh nghĩa là `notification` chỉ dùng `UserApi`, `UserSummary` và `PasswordResetRequested`, đều ở gói gốc của `identity`. `healthIsPublic` (trong `SecurityConfigTest`) xanh nghĩa là health check của mail đã tắt.

- [ ] **Step 9: Commit**

```bash
git add backend/pom.xml backend/src .env.example docker-compose.yml
git commit -m "feat(notification): add async email framework and password reset email"
```

---

### Task 7: Tài khoản mẫu, chạy thử cả hệ thống và cập nhật tài liệu

**Files:**
- Create: `backend/src/main/resources/db/seed/V105__seed_accounts.sql`
- Modify: `backend/src/main/resources/application-dev.properties`, `CLAUDE.md`, `docs/superpowers/plans/2026-10-09-00-roadmap.md`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/identity/SeedAccountsTest.java`

**Interfaces:**
- Consumes: `POST /api/auth/login`, `GET /api/me` (Task 1); `TestUsers.login` (Task 1).
- Produces:
  - Seed `V105`: 3 tài khoản `admin@demo.local`, `staff@demo.local`, `customer@demo.local`, cùng mật khẩu `Demo@1234`.
  - Profile `dev` nạp `classpath:db/seed` và bật `spring.flyway.out-of-order`. Từ Plan 05 trở đi, chỉ cần thả file `V10x__seed_*.sql` vào `db/seed`.

- [ ] **Step 1: Viết test cho seed**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/identity/SeedAccountsTest.java`:

```java
package vn.edu.uit.flightbooking.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;

/** Seed chỉ nạp ở profile dev; test chạy file seed trong transaction rồi rollback để kiểm tra hash và vai trò. */
@IntegrationTest
@Transactional
@Sql("classpath:db/seed/V105__seed_accounts.sql")
class SeedAccountsTest {

	@Autowired
	MockMvc mvc;

	@Test
	void demoAccountsLogInWithDocumentedPassword() throws Exception {
		for (String role : new String[] { "admin", "staff", "customer" }) {
			var session = TestUsers.login(mvc, role + "@demo.local", "Demo@1234");
			mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.role").value(role.toUpperCase()));
		}
	}

}
```

Test này không trái quy ước "test không dựa vào seed", vì đối tượng được kiểm tra chính là file seed. `@Sql` chạy file seed trong transaction của test, và hết test thì mọi thứ được rollback.

- [ ] **Step 2: Chạy test để thấy test đỏ**

```bash
cd backend && ./mvnw -B test -Dtest=SeedAccountsTest
```

Expected: `Tests run: 1, Failures: 0, Errors: 1`, với lỗi `CannotReadScriptException: Cannot read SQL script from class path resource [db/seed/V105__seed_accounts.sql]`.

- [ ] **Step 3: Viết seed tài khoản mẫu**

Tạo `backend/src/main/resources/db/seed/V105__seed_accounts.sql`:

```sql
-- Tài khoản mẫu (BACKEND_SCHEMA §8.5). Mật khẩu của cả 3 tài khoản: Demo@1234 (chỉ lưu hash BCrypt).
INSERT INTO users (email, password_hash, full_name, phone, role) VALUES
    ('admin@demo.local',    '$2a$10$mreYm.pOnhP/p1QIEh5GHe0Lc18Mf6EUrQMCZB4c064MflGwIiVMy', 'Quản trị viên Demo', '0900000001', 'ADMIN'),
    ('staff@demo.local',    '$2a$10$d4cEhHAMVgXhX9qAVmatWu1dKvRslYDt95UIw7uQfCZ8jXC44fLTe', 'Nhân viên Demo',     '0900000002', 'STAFF'),
    ('customer@demo.local', '$2a$10$rSNzjIMI5DsXYU0zbPKUq.zuWUTIQ0HP7U2hMoIhVTtHx31n2CRxu', 'Khách hàng Demo',    '0900000003', 'CUSTOMER');
```

Ba hash trên đều là BCrypt (cost 10) của `Demo@1234`, sinh bằng `BCrypt.hashpw("Demo@1234", BCrypt.gensalt(10))`. Muốn sinh lại thì chạy lệnh dưới đây từ thư mục gốc repo:

```bash
printf 'System.out.println(org.springframework.security.crypto.bcrypt.BCrypt.hashpw("Demo@1234", org.springframework.security.crypto.bcrypt.BCrypt.gensalt(10)));\n/exit\n' \
  | jshell --class-path ~/.m2/repository/org/springframework/security/spring-security-crypto/7.1.1/spring-security-crypto-7.1.1.jar -q
```

Mỗi lần chạy cho một hash khác nhau (do salt ngẫu nhiên), nhưng hash nào cũng khớp `Demo@1234`.

Flyway không hiểu nhầm `$2a$10$...` là placeholder, vì placeholder của Flyway có dạng `${...}`.

- [ ] **Step 4: Cho profile `dev` nạp seed**

Thêm vào cuối `backend/src/main/resources/application-dev.properties`:

```properties

# Dữ liệu demo (TDD §6.12). Các plan thêm seed không theo thứ tự số (V105 trước V100), nên cần out-of-order.
spring.flyway.locations=classpath:db/migration,classpath:db/seed
spring.flyway.out-of-order=true
```

Plan 05 sẽ thêm `V100`–`V103`, khi DB đã ở `V105`. Nếu không có `out-of-order`, Flyway sẽ báo lỗi `Detected resolved migration not applied to database`.

- [ ] **Step 5: Chạy toàn bộ test**

```bash
cd backend && ./mvnw -B clean verify
```

Expected: `Tests run: 59, Failures: 0, Errors: 0` và `BUILD SUCCESS`.

- [ ] **Step 6: Chạy thử cả hệ thống**

Dùng `.env` sẵn có. Khi chạy trong Docker, backend lấy `MAIL_HOST=mailpit` từ `docker-compose.yml`.

```bash
docker compose up -d --build
docker compose ps
docker compose logs backend | grep -E "Migrating|Successfully applied"
```

Expected:
- Cả `postgres`, `mailpit` và `backend` đều `(healthy)`.
- Log có `Migrating schema "public" to version "105 - seed accounts"`. Nếu volume đã có V1 từ Plan 01, Flyway chỉ áp thêm V105.

Sau đó gọi API bằng `curl`. Cookie lưu vào `/tmp/jar.txt`; header `X-XSRF-TOKEN` lấy từ cookie `XSRF-TOKEN`:

```bash
J=/tmp/jar.txt; B=http://localhost:8080; rm -f $J
curl -s -c $J -b $J -o /dev/null -w "csrf %{http_code}\n" $B/api/auth/csrf
X=$(awk '$6=="XSRF-TOKEN"{print $7}' $J)
curl -s -c $J -b $J -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.local","password":"Demo@1234"}' $B/api/auth/login; echo
curl -s -c $J -b $J "$B/api/admin/users?q=demo&size=2"; echo
curl -s -c $J -b $J -X PUT -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"value":20}' -w "put %{http_code}\n" $B/api/admin/settings/booking.hold_minutes
curl -s -c $J -b $J -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"email":"customer@demo.local"}' -w "forgot %{http_code}\n" $B/api/auth/forgot-password
```

Expected:
- `csrf 204`.
- Đăng nhập trả `{"id":1,"email":"admin@demo.local","fullName":"Quản trị viên Demo","phone":"0900000001","role":"ADMIN","status":"ACTIVE"}`.
- Danh sách tài khoản trả 2 tài khoản mới nhất trước (`customer`, rồi `staff`) và phần `page`:

```json
{"content":[{"id":3,"email":"customer@demo.local","fullName":"Khách hàng Demo","phone":"0900000003","role":"CUSTOMER","status":"ACTIVE"},{"id":2,"email":"staff@demo.local","fullName":"Nhân viên Demo","phone":"0900000002","role":"STAFF","status":"ACTIVE"}],"page":{"size":2,"number":0,"totalElements":3,"totalPages":2}}
```

- Sửa tham số trả `put 204`.
- Quên mật khẩu trả `forgot 200`.

Mở <http://localhost:8025>. Mailpit có email "Đặt lại mật khẩu SkyLine" gửi tới `customer@demo.local`, trong đó có nút dẫn tới `http://localhost:3000/reset-password?token=...`.

Đặt lại tham số đã sửa về mặc định, rồi dừng stack. Volume dữ liệu được giữ nguyên:

```bash
curl -s -c $J -b $J -X PUT -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"value":15}' -o /dev/null $B/api/admin/settings/booking.hold_minutes
docker compose down
```

- [ ] **Step 7: Cập nhật CLAUDE.md**

Trong `CLAUDE.md`, mục `## Commands`, thêm dòng sau ngay sau dòng `- Full stack: ...`:

```markdown
- Demo accounts (profile `dev`, seed `V105`): `admin@demo.local`, `staff@demo.local`, `customer@demo.local`, password `Demo@1234`.
```

Thay đoạn `Test conventions: ...` bằng:

```markdown
Test conventions: integration tests use `@IntegrationTest` (one shared Spring context and one PostgreSQL 18 container). Send CSRF with `TestCsrf.csrf(mvc)`, never `SecurityMockMvcRequestPostProcessors.csrf()`: it swaps the shared `CsrfFilter`'s token repository and breaks later tests. Log in with `TestUsers.loggedIn(mvc, jdbc, Role.X)` (real `POST /api/auth/login`) and pass `.session(user.session())`. Sent emails are captured by `TestMailSender.sentTo(email)`; they are only sent after commit, so email tests must not be `@Transactional` and clean up after themselves. Tests create their own data and never read `.env`.
```

- [ ] **Step 8: Cập nhật lộ trình**

Trong `docs/superpowers/plans/2026-10-09-00-roadmap.md`:

1. **Bảng đầu tài liệu:** sửa dòng "Plan chi tiết đã có" thành:

```markdown
| Plan chi tiết đã có | [Plan 01 — Nền móng backend](2026-10-09-01-backend-foundation.md) · [Plan 02 — Tài khoản, tham số, khung email](2026-10-09-02-accounts-settings-email.md) |
```

2. **§2:** ở dòng Plan 02, đổi `☐` cuối dòng thành `☑`.

3. **§6:** thay dòng `| 02 | Dự kiến: ... |` bằng:

```markdown
| 02 | `Role` (`CUSTOMER`, `STAFF`, `ADMIN`) và `CurrentUser(id, role)` ở `common`. `CurrentUser` là principal trong session; controller nhận bằng `@AuthenticationPrincipal CurrentUser me`. `BusinessException.invalidField(field, message)` (400 có `errors`). `SettingKey.of(key)`, `SettingsApi.list()`, `SettingsApi.update(key, value, updatedBy)`. `identity.UserApi.get(id) → UserSummary(id, email, fullName)`. Sự kiện `identity.PasswordResetRequested(userId, rawToken)`. Khung email: `notification.infra.Mailer.send(to, subject, template, variables)` + listener `@ApplicationModuleListener` (đã bật `@EnableAsync`), template ở `templates/email/`. Phân trang trả `new PagedModel<>(page)`. Seed `V105` (3 tài khoản, mật khẩu `Demo@1234`); profile `dev` nạp `db/seed` với `out-of-order`. Test: `TestUsers.loggedIn(mvc, jdbc, role)`, `TestUsers.login(mvc, email, password)`, `TestMailSender.sentTo(email)` |
```

4. **§7:** thêm mục sau ngay sau mục 1 (mục cũ 2–5 lùi thành 3–6):

```markdown
2. **Plan 02 có 3 điểm cố ý khác TDD** (chi tiết ở [Plan 02](2026-10-09-02-accounts-settings-email.md#điểm-cố-ý-khác-tdd)):
   - Đăng nhập tự kiểm tra BCrypt, không dùng `DaoAuthenticationProvider`. Lý do: provider đó báo "tài khoản bị khoá" trước khi kiểm tra mật khẩu, làm lộ trạng thái tài khoản.
   - Chặn phiên của tài khoản bị khoá bằng `HandlerInterceptor` ở `identity`, không bằng filter của Spring Security, vì `common` không được phụ thuộc `identity`.
   - Nhập sai mật khẩu hiện tại khi đổi mật khẩu trả 400 `VALIDATION_FAILED` cho trường `currentPassword`, không trả 401.
```

- [ ] **Step 9: Commit**

```bash
git add backend/src CLAUDE.md docs/superpowers/plans/2026-10-09-00-roadmap.md
git status --short
git commit -m "feat(identity): seed demo accounts for dev profile and document Plan 02"
```

Expected: `git status --short` **không** liệt kê `.env`.

---

## Kiểm tra cuối plan

- [ ] `cd backend && ./mvnw -B clean verify` → `BUILD SUCCESS`, 59 test.
- [ ] `docker compose up -d --build` → 3 service `(healthy)`. Đăng nhập được bằng `admin@demo.local` / `Demo@1234`; email đặt lại mật khẩu xuất hiện trong Mailpit.
- [ ] CI trên GitHub xanh (nếu đã push).
- [ ] Lộ trình đã đánh dấu Plan 02 (§2) và cập nhật hợp đồng (§6) theo code thật.
