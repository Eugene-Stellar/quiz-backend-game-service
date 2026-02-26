package eugenestellar.controller;

import eugenestellar.config.UserPrincipal;
import eugenestellar.service.GameService;
import eugenestellar.service.QuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameRestController {

  private final GameService gameService;
  private final QuestionService questionService;

  public GameRestController(GameService gameService, QuestionService questionService) {
    this.gameService = gameService;
    this.questionService = questionService;
  }

  @GetMapping("/my_status")
  public ResponseEntity<Map<String, String>> getPlayerStatus(@AuthenticationPrincipal UserPrincipal user) {

    String userStatus = gameService.getPlayerState(user.id());
    return ResponseEntity.ok().body(Map.of("status", userStatus));
  }

  @GetMapping("/topics")
  public ResponseEntity<List<String>> getAllTopics() {

    List<String> topics = questionService.findAllTopics();
    topics.add("random");

    return ResponseEntity.ok().body(topics);
  }
}