package eugenestellar.backendgame.controller;

import eugenestellar.backendgame.model.dto.GameRoomDto;
import eugenestellar.backendgame.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class GameWsController {

  private final GameService gameService;

  public GameWsController(GameService gameService) {
    this.gameService = gameService;
  }

  @MessageMapping("/join-game_room") // destination for a client
  @SendToUser("/queue/reply") // destination for a server
  public GameRoomDto putUserIntoRoom(
      @Header("simpSessionAttributes") Map<String, Object> attributes ) {

    String username = (String) attributes.get("username");
    Long userId = (Long) attributes.get("userId");

    return gameService.joinGameRoom(username, userId);
  }

  @MessageMapping("/create-game_room/{qQuantity}")
  @SendToUser("/queue/reply")
  public GameRoomDto createRoom(
      @DestinationVariable Integer qQuantity,
      @Header("simpSessionAttributes") Map<String, Object> attributes) {

    String username = (String) attributes.get("username");
    Long userId = (Long) attributes.get("userId");

    return gameService.createRoom(username, userId, qQuantity);
  }

  @MessageMapping("/answer/{qId}/{answerId}/{roomId}")
  public void answerQ (
      @DestinationVariable String roomId,
      @DestinationVariable Long qId,
      @DestinationVariable Long answerId,
      @Header("simpSessionAttributes") Map<String, Object> attributes) {

    Long userId = (Long) attributes.get("userId");

    gameService.answerQ(qId, userId, answerId, roomId);
  }

  @MessageMapping("/leave-game_room/{roomId}")
  public void kickUserFromRoom(
      @DestinationVariable String roomId,
      @Header("simpSessionAttributes") Map<String, Object> attributes) {

    Long userId = (Long) attributes.get("userId");

    gameService.leaveGameRoom(userId, roomId);
  }
}