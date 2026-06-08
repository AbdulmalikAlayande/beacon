package io.github.abdulmalikalayande.beacon.core.template;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;
import io.github.abdulmalikalayande.beacon.api.dto.NotificationTemplate;
import io.github.abdulmalikalayande.beacon.api.dto.RenderedNotification;
import io.github.abdulmalikalayande.beacon.api.port.TemplateEngine;

import java.util.Map;

public class AdvancedTemplateEngine implements TemplateEngine {
	
	
	@Override
	public RenderedNotification render(NotificationTemplate template, NotificationPreference preference, Map<String, String> context) {
		return null;
	}
}
