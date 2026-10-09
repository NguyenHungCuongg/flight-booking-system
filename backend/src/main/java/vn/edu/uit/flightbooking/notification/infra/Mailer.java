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
