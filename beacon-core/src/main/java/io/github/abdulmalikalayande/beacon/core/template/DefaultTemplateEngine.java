package io.github.abdulmalikalayande.beacon.core.template;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;
import io.github.abdulmalikalayande.beacon.api.dto.NotificationTemplate;
import io.github.abdulmalikalayande.beacon.api.dto.RenderedNotification;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.port.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultTemplateEngine implements TemplateEngine {

	private static final Logger log = LoggerFactory.getLogger(DefaultTemplateEngine.class);

	public static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
	
	@Override
	public RenderedNotification render(NotificationTemplate template, NotificationPreference preference, Map<String, String> context) {
		
		String subject = renderString(template.subjectTemplate(), context);
		String title = renderString(template.titleTemplate(), context);
		String body = renderString(template.bodyTemplate(), context);
		String to = extractDestination(preference, template.channel());
		
		return new RenderedNotification(template.channel(), to, subject, title, body, null);
	}
	
	private String extractDestination(NotificationPreference preference, NotificationChannel channel) {
		if (preference == null) return null;
		return switch (channel) {
			case SMS -> preference.phoneNumber();
			case EMAIL -> preference.email();
			case PUSH -> preference.pushToken();
		};
	}
	
	private String renderString(String templateString, Map<String, String> context) {
		if (templateString == null || templateString.isBlank()) {
			return templateString;
		}
		Matcher patterMatcher = PLACEHOLDER_PATTERN.matcher(templateString);
		StringBuilder stringBuilder = new StringBuilder();
		
		while (patterMatcher.find()) {
			String key = patterMatcher.group(1).trim();
			String value = (context != null) ? context.get(key) : null;
			String replacementValue;

			if (value != null) {
				replacementValue = value;
			} else {
				replacementValue = patterMatcher.group(0);
				log.warn("Template placeholder '{}' has no usable value in context; left unrendered.", key);
			}

			patterMatcher.appendReplacement(stringBuilder, Matcher.quoteReplacement(replacementValue));
		}
		patterMatcher.appendTail(stringBuilder);
		
		return stringBuilder.toString();
	}
}
