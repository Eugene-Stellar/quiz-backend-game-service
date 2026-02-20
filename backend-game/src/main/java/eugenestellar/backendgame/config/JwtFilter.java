package eugenestellar.backendgame.config;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import eugenestellar.backendgame.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
  private final ObjectMapper objectMapper;
  private final JwtUtil jwtUtil;

  public JwtFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
    this.jwtUtil = jwtUtil;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/ws-game");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

    String valueOfHeader = request.getHeader("Authorization");

    if (valueOfHeader != null && valueOfHeader.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        String token = valueOfHeader.substring(7);
        DecodedJWT jwt = jwtUtil.jwtValidation(token);
        String tokenType = jwt.getClaim("type").asString();
        if (!tokenType.equals("access") && !tokenType.equals("service"))
          throw new JWTVerificationException("Invalid token type.");

        String username = jwt.getSubject();
        List<String> rolesFromToken = jwt.getClaim("roles").isNull() ? List.of()
            : jwt.getClaim("roles").asList(String.class);

        List<SimpleGrantedAuthority> authorities = rolesFromToken.stream()
            .map(SimpleGrantedAuthority::new) // role -> new SimpleGrantedAuthority(role)
            .toList();

        Object principal;
        if (tokenType.equals("access")) {
          principal = new UserPrincipal(jwt.getClaim("userId").asLong(), username);
        } else {
          principal = username;
        }

        // for a service token the Principal is just a username
        Authentication auth = new UsernamePasswordAuthenticationToken(
            principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (JWTVerificationException ex) {
        SecurityContextHolder.clearContext();
        log.warn("Invalid JWT token. {}", ex.getMessage());
        sendCustomError(response, "Invalid JWT token.");
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  private void sendCustomError(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    response.addHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"" + message + "\"");

    response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", message)));
  }
}
