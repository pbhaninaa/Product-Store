package com.productstore.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * {@code @Scheduled} jobs must run on the app's own {@code jobs-} threads, not on the WebSocket broker's
 * heartbeat scheduler, even though both are {@code TaskScheduler} beans in the context.
 */
@SpringJUnitConfig(SchedulingConfigTest.Context.class)
class SchedulingConfigTest {

  static final CountDownLatch RAN = new CountDownLatch(1);
  static final AtomicReference<String> THREAD = new AtomicReference<>();

  @Configuration
  @Import(SchedulingConfig.class)
  static class Context {
    /** Stands in for the WebSocket broker's scheduler that Spring registers under this name. */
    @Bean(name = "messageBrokerTaskScheduler")
    ThreadPoolTaskScheduler messageBrokerTaskScheduler() {
      ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
      s.setThreadNamePrefix("MessageBroker-");
      return s;
    }

    @Bean
    Job job() {
      return new Job();
    }
  }

  static class Job {
    @Scheduled(fixedDelay = 50)
    void run() {
      THREAD.compareAndSet(null, Thread.currentThread().getName());
      RAN.countDown();
    }
  }

  @Autowired ThreadPoolTaskScheduler taskScheduler;

  @Test
  void scheduledJobsRunOnTheJobsScheduler() throws Exception {
    assertThat(RAN.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(THREAD.get()).startsWith("jobs-");
    assertThat(taskScheduler.getThreadNamePrefix()).isEqualTo("jobs-");
  }
}
