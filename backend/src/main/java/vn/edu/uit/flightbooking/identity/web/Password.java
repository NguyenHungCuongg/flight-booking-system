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
