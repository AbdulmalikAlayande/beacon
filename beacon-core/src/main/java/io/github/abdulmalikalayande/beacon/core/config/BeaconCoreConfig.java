package io.github.abdulmalikalayande.beacon.core.config;

import io.github.abdulmalikalayande.beacon.api.port.ProviderCircuitBreaker;
import io.github.abdulmalikalayande.beacon.core.resilience.DefaultProviderCircuitBreaker;
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

	/**
	 * Provides a default in-memory {@link ProviderCircuitBreaker} bean if no other circuit breaker
	 * is defined in the application context.
	 *
	 * @param clock the injected Clock bean
	 * @return default circuit breaker instance
	 */
	@Bean
	@ConditionalOnMissingBean(ProviderCircuitBreaker.class)
	public ProviderCircuitBreaker providerCircuitBreaker(Clock clock) {
		return new DefaultProviderCircuitBreaker(clock);
	}
}
