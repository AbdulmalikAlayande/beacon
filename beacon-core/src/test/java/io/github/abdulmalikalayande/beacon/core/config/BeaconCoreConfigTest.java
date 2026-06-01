package io.github.abdulmalikalayande.beacon.core.config;


import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.time.Clock;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BeaconCoreConfigTest {
    
    ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(BeaconCoreConfig.class);
    ApplicationContext applicationContext = new GenericApplicationContext();
    
    @Test
    public void contextLoads_ClockBeanIsPresent() {
//        Clock clockBean = applicationContext.getBean(Clock.class);
//        assertThat(clockBean).isNotNull();
//        assertNotNull(clockBean);
//        assertThat(applicationContext).hasSingleBean(Clock.class);
        
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            Clock clockBean = context.getBean(Clock.class);
            assertNotNull(clockBean);
        });
    }

}