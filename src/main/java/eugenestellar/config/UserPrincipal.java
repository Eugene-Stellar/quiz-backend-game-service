package eugenestellar.config;

import java.security.Principal;

public record UserPrincipal(Long id, String username) implements Principal {

  @Override
  public String getName() {
    return username;
  }
}