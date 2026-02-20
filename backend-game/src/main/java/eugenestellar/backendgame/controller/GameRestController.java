package eugenestellar.backendgame.controller;

import eugenestellar.backendgame.config.UserPrincipal;
import eugenestellar.backendgame.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameRestController {

  private final GameService gameService;

  public GameRestController(GameService gameService) {
    this.gameService = gameService;
  }

  @GetMapping("/my_status")
  public ResponseEntity<Map<String, String>> getPlayerStatus(@AuthenticationPrincipal UserPrincipal user) {

    String userStatus = gameService.getPlayerState(user.getId());
    return ResponseEntity.ok().body(Map.of("status", userStatus));
  }
}