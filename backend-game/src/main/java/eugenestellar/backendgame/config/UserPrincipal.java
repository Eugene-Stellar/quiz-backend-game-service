package eugenestellar.backendgame.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.security.Principal;

@Getter
@AllArgsConstructor
public class UserPrincipal implements Principal {

  private Long id;
  private String username;

  @Override
  public String getName() {
    return username;
  }
}