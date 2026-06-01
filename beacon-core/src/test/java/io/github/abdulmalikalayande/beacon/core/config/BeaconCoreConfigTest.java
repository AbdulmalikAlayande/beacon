package io.github.abdulmalikalayande.beacon.core.config;


import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BeaconCoreConfigTest {
    
    /**
     * An instance of {@code ApplicationContextRunner} configured with the {@code BeaconCoreConfig}
     * class to facilitate testing of application context initialization and bean definitions.
     *
     * <p>This runner is used to simulate the Spring application context for unit testing,
     * allowing for validation of bean presence, configuration, and behavior within the
     * {@code BeaconCoreConfig} context. It simplifies the testing of conditions under various
     * application context configurations.
     *
     * <p>The configuration provided includes the {@code BeaconCoreConfig} class, which is responsible
     * for defining beans used in the application, such as the default {@code Clock} bean.
     */
    ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(BeaconCoreConfig.class)
    );
    
    /**
     * Tests that the application context properly loads and contains a single bean of type {@code Clock}.
     <p>
     Validates the presence of the default {@code Clock} bean in the context, ensures it is uniquely defined,
     * and asserts that it is the same instance as the bean associated with the name "clock".
     <p>
     * This test uses {@code ApplicationContextRunner} to run the application context with the specified
     * configuration class and perform assertions on the context's state.
     */
    @Test
    public void contextLoads_DefaultClockBeanIsPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            Clock clockBean = context.getBean(Clock.class);
            assertThat(context).getBean("clock").isSameAs(clockBean);
            assertNotNull(clockBean);
        });
    }
    
    /**
     * Verifies that the default {@code Clock} bean in the application context exists and is configured
     * to use the UTC time zone.
     <p>
     * This test ensures that the {@code Clock} bean created by the {@code BeaconCoreConfig} configuration
     * class is properly initialized and its time zone matches the expected UTC zone.
     <p>
     * The test uses {@code ApplicationContextRunner} to initialize the application context with the
     * provided configuration and checks:
     * - The {@code Clock} bean is not {@code null}.
     * - The time zone of the {@code Clock} bean matches {@code Clock.systemUTC().getZone()}.
     */
    @Test
    public void contextLoads_DefaultExistingClockBeanHasUTCAsItsZone() {
        contextRunner.run(context -> {
            Clock clockBean = context.getBean(Clock.class);
            assertThat(clockBean).isNotNull();
            assertThat(clockBean.getZone()).isEqualTo(Clock.systemUTC().getZone());
            assertThat(clockBean.getZone()).isEqualTo(ZoneOffset.UTC);
        });
    }
    
    /**
     * Verifies that the default {@code Clock} bean created by the {@code BeaconCoreConfig}
     * class is a singleton within the application context.
     *
     * <p>
     * This test ensures the following:
     * - The application context contains exactly one bean of type {@code Clock}.
     * - The {@code Clock} bean retrieved from the context is the same instance each time it is accessed.
     *
     * <p>
     * The test utilizes {@code ApplicationContextRunner} to initialize the application context
     * with the specified configuration and performs assertions to validate the singleton nature
     * of the {@code Clock} bean.
     */
    @Test
    public void contextLoads_DefaultClockBeanIsSingleton() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class)).isSameAs(context.getBean(Clock.class));
        });
    }
    
    /**
     * Ensures that when a custom {@code Clock} bean is provided in the application context,
     * the default {@code Clock} bean defined in {@code BeaconCoreConfig} is not created.
     <p>
     * This test validates the following:
     * - The application context contains exactly one {@code Clock} bean.
     * - The {@code Clock} bean present in the context corresponds to the custom bean configuration
     *   provided by the {@code BeanCoreTestConfig} class.
     * - The zone of the custom {@code Clock} bean matches the expected custom configuration
     *   (e.g., {@code ZoneId.of("Africa/Lagos")}).
     <p>
     * The test uses {@code ApplicationContextRunner} to initialize the application context
     * with both {@code BeaconCoreConfig} and {@code BeanCoreTestConfig} configurations
     * and performs assertions to verify the absence of the default {@code Clock} bean.
     */
    @Test
    public void whenCustomClockBeanIsSupplied_DefaultClockBeanIsNotCreated() {
        contextRunner.withUserConfiguration(BeaconCoreTestConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(Clock.class);
                Clock clockBean = context.getBean(Clock.class);
                assertThat(clockBean).isNotNull();
                assertThat(context).getBean("testClock").isSameAs(clockBean);
                assertThat(clockBean.getZone()).isEqualTo(ZoneId.of("Africa/Lagos"));
                assertEquals(clockBean.instant(), Instant.parse("2026-05-31T03:00:00Z"));
                assertEquals(clockBean.instant().getEpochSecond(), Instant.parse("2026-05-31T03:00:00Z").getEpochSecond());
            });
    }

}