package eugenestellar.backendgame.websocket;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import eugenestellar.backendgame.repository.UserInfoRepo;
import eugenestellar.backendgame.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
public class CustomHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtUtil jwtUtil;
  private final UserInfoRepo userInfoRepo;
  private static final Logger log = LoggerFactory.getLogger(CustomHandshakeInterceptor.class);

  public CustomHandshakeInterceptor(JwtUtil jwtUtil, UserInfoRepo userInfoRepo) {
    this.jwtUtil = jwtUtil;
    this.userInfoRepo = userInfoRepo;
  }

  @Override
  public boolean beforeHandshake(ServerHttpRequest request,
                                 ServerHttpResponse response,
                                 WebSocketHandler wsHandler,
                                 Map<String, Object> attributes) throws Exception {


    String valueOfHeader = request.getHeaders().getFirst("Authorization");

    if (valueOfHeader != null && valueOfHeader.startsWith("Bearer ")) {
      String token = valueOfHeader.substring(7);
      return tokenValidationAndSetAttributes(token, response, attributes);
    }
    String query = request.getURI().getQuery();
    if (query != null && !query.isBlank()) {
      for (String param : query.split("&")) {
        if (param.startsWith("token=")) {
          String token = param.substring(6);
          return tokenValidationAndSetAttributes(token, response, attributes);
        }
      }
    }
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    log.warn("Handshake rejected: missing or invalid Authorization header");
    return false;
  }

  private boolean tokenValidationAndSetAttributes(String token, ServerHttpResponse response, Map<String, Object> attributes) {

    try {
      DecodedJWT jwt = jwtUtil.jwtValidation(token);

      String tokenType = jwt.getClaim("type").asString();

      List<String> tokenRoles = jwt.getClaim("roles").asList(String.class);

      if (!tokenType.equals("access") || tokenRoles == null || tokenRoles.contains("ROLE_AUTH_SERVICE")) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        log.warn("Handshake rejected: Invalid JWT token. Invalid role or type of the JWT");
        return false;
      }

      String username = jwt.getSubject();
      Long userId = jwt.getClaim("userId").asLong();

      // can't connect because there's no such user in DB even if its token is valid
      if (userInfoRepo.findById(userId).isEmpty()) {
        log.warn("Handshake rejected: User with ID {} wasn't found in DB", userId);
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }
      attributes.put("username", username);
      attributes.put("userId", userId);
      return true;
    } catch (JWTVerificationException e) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      log.warn("Handshake rejected: Invalid JWT token. {}", e.getMessage());
      return false;
    }
  }

  @Override
  public void afterHandshake(ServerHttpRequest request,
                             ServerHttpResponse response,
                             WebSocketHandler wsHandler,
                             Exception exception) {

  }
}