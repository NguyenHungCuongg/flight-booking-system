package vn.edu.uit.flightbooking.booking.domain;

import java.security.SecureRandom;

/** BR-46: mã đặt chỗ 6 ký tự, bỏ các ký tự dễ nhầm 0, O, 1, I (khớp CHECK của {@code bookings.code}). */
final class BookingCodes {

	static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final SecureRandom RANDOM = new SecureRandom();

	private BookingCodes() {
	}

	static String next() {
		char[] code = new char[6];
		for (int i = 0; i < code.length; i++) {
			code[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
		}
		return new String(code);
	}

}
