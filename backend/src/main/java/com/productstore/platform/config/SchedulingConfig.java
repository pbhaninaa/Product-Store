package com.productstore.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * A scheduler of its own for {@code @Scheduled} jobs (unpaid order expiry, payment reconciliation).
 *
 * <p>Without it, Spring picks the only {@code TaskScheduler} bean in the context, which is the WebSocket
 * broker's heartbeat scheduler, so slow jobs (Paystack calls) would share threads with STOMP heartbeats.
 * {@code @Scheduled} uses the bean named {@code taskScheduler} when there are several.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

  @Bean(name = "taskScheduler")
  public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("jobs-");
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }
}
