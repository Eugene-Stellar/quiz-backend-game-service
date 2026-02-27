package eugenestellar.controller;

import eugenestellar.service.GameRoundService;
import eugenestellar.service.GameService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Controller
@Validated
public class GameWsController {

  private final GameService gameService;
  private final GameRoundService gameRoundService;

  public GameWsController(GameService gameService,
                          GameRoundService gameRoundService) {
    this.gameService = gameService;
    this.gameRoundService = gameRoundService;
  }

  @MessageMapping("/join-game_room/{qQuantity}/{topic}") // destination for a client (/app)
  @SendToUser("/queue/reply") // destination for a server to send msg to particular user
  public Object putUserIntoRoom(
      @Min(value = 3, message = "Minimum 3 questions")
      @Max(value = 15, message = "Maximum 15 questions")
      @DestinationVariable Integer qQuantity,
      @DestinationVariable String topic,
      @Header("simpSessionAttributes") Map<String, Object> attributes) {

    String username = (String) attributes.get("username");
    Long userId = (Long) attributes.get("userId");

    return gameService.joinGameRoom(username, userId, topic, qQuantity);
  }

  @MessageMapping("/reconnect")
  @SendToUser("/queue/reply")
  public Object reconnect(@Header("simpSessionAttributes") Map<String, Object> attributes) {
    Long userId = (Long) attributes.get("userId");

    return gameService.reconnectUser(userId);
  }

  @MessageMapping("/answer/{qId}/{answerId}/{roomId}")
  public void answerQ (
      @DestinationVariable String roomId,
      @DestinationVariable Long qId,
      @DestinationVariable Long answerId,
      @Header("simpSessionAttributes") Map<String, Object> attributes) {

    Long userId = (Long) attributes.get("userId");

    gameRoundService.answerQ(qId, userId, answerId, roomId);
  }

  @MessageMapping("/leave-game_room/{roomId}")
  public void kickUserFromRoom(
      @DestinationVariable String roomId,
      @Header("simpSessionAttributes") Map<String, Object> attributes) {

    Long userId = (Long) attributes.get("userId");

    gameService.leaveGameRoom(userId, roomId);
  }
}