package eugenestellar.backendgame.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtUtil {

  @Value("${SECRET_WORD}")
  private String secretWord;

  public DecodedJWT jwtValidation(String token) throws JWTVerificationException {

    JWTVerifier jwtVerifier = JWT
        .require(Algorithm.HMAC256(secretWord))
        .withIssuer("auth-service")
        .build();

    return jwtVerifier.verify(token);
  }
}