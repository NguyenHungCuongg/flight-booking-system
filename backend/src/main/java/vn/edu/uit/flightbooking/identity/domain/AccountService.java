package vn.edu.uit.flightbooking.identity.domain;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

	/** FR-110, BR-101: Admin chỉ tạo tài khoản vận hành. */
	@Transactional
	public User createOperator(String email, String password, String fullName, String phone, Role role) {
		if (role == Role.CUSTOMER) {
			throw BusinessException.invalidField("role", "Chỉ tạo được tài khoản STAFF hoặc ADMIN");
		}
		return create(email, password, fullName, phone, role);
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

	@Transactional(readOnly = true)
	public boolean isActive(long id) {
		return users.findStatusById(id).filter(status -> status == UserStatus.ACTIVE).isPresent();
	}

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
