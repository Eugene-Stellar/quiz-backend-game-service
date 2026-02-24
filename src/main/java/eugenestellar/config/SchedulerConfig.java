package eugenestellar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@EnableScheduling
@Configuration
public class SchedulerConfig {

  @Bean
  public TaskScheduler taskScheduler() {

    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    // Pool size (defines how many scheduled tasks can run concurrently): 100 threads,
    // it's enough to process a plenty of rooms, since timerExpired() executes swiftly
    scheduler.setPoolSize(100); //

    scheduler.setThreadNamePrefix("GameTimer-"); // for debugging
    scheduler.initialize();
    return scheduler;
  }
}