package eugenestellar.config;

import eugenestellar.websocket.CustomHandshakeHandler;
import eugenestellar.websocket.CustomHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final CustomHandshakeInterceptor customHandshakeInterceptor;
  private final String frontendUrl;

  public WebSocketConfig(CustomHandshakeInterceptor customHandshakeInterceptor, @Value("${FRONTEND_URL}")String frontendUrl) {
    this.customHandshakeInterceptor = customHandshakeInterceptor;
    this.frontendUrl = frontendUrl;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue"); // /topic for broadcasting, /queue for private messaging
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws-game")
        .addInterceptors(customHandshakeInterceptor)
        .setHandshakeHandler(new CustomHandshakeHandler()) // is used to set custom Principal and tie WebSocket session with user
        .setAllowedOrigins(frontendUrl);
  }
}