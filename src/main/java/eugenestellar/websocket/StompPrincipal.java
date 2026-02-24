package eugenestellar.websocket;

import java.security.Principal;

// ties ws session with user, it allows to work private messaging (/queue)
public class StompPrincipal implements Principal {
  private final String name;

  public StompPrincipal(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }
}