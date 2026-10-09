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
