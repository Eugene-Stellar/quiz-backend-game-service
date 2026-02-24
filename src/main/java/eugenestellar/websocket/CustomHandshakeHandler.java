package eugenestellar.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

// sets custom Principal for WebSocket session (allows to work private messaging (/queue) & convertAndSendToUser routing).
// principal in WebSocket ≈ Authentication.getPrincipal() in HTTP SecurityContext
public class CustomHandshakeHandler extends DefaultHandshakeHandler {
  @Override
  protected Principal determineUser(ServerHttpRequest request,
                                    WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
    Object usernameObj = attributes.get("username");
    String username = usernameObj == null ? "unknown"
        : usernameObj.toString();

    return new StompPrincipal(username);
  }
}