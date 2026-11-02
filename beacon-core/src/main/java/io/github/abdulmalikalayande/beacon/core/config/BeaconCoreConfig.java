package io.github.abdulmalikalayande.beacon.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BeaconCoreConfig {
	
	/**
	 * Provides a Clock bean that supplies the current time based on the UTC time zone
	 * for all time-dependent components in Beacon. This method will create and return a Clock instance if no other Clock bean
	 * is already defined in the application context.
	 *
	 * @return a Clock instance representing the system clock in the UTC time zone.
	 */
	@Bean
	@ConditionalOnMissingBean(Clock.class)
	public Clock clock() {
		return Clock.systemUTC();
	}
}
