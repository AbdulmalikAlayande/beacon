package io.github.abdulmalikalayande.beacon.core.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@TestConfiguration
public class BeaconCoreTestConfig {
	
	@Bean
	public Clock testClock() {
		return Clock.fixed(Instant.parse("2026-05-31T03:00:00Z"), ZoneId.of("Africa/Lagos"));
	}
}
