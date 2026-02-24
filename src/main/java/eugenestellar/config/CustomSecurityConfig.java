package eugenestellar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CustomSecurityConfig {

  private final List<String> corsAllowedOrigins;
  private final JwtFilter jwtFilter;

  public CustomSecurityConfig(JwtFilter jwtFilter, @Value("${CORS_ALLOWED_ORIGINS}")List<String> corsAllowedOrigins) {
    this.jwtFilter = jwtFilter;
    this.corsAllowedOrigins = corsAllowedOrigins;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
          .requestMatchers("/error", "/ws-game/**").permitAll()
          .requestMatchers("/user_info/**").hasRole("AUTH_SERVICE")
          .requestMatchers("/api/**").hasRole("USER")
          .anyRequest().authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {

    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedMethods(List.of("POST", "HEAD", "GET", "DELETE", "OPTIONS", "PUT"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowedOriginPatterns(corsAllowedOrigins);
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}