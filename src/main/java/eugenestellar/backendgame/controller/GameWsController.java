package eugenestellar.backendgame.controller;

import eugenestellar.backendgame.model.dto.GameRoomDto;
import eugenestellar.backendgame.model.gameEntity.GameRoom;
import eugenestellar.backendgame.service.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Controller
@Validated
public class GameWsController {

  private final GameService gameService;

  public GameWsController(GameService gameService) {
    this.gameService = gameService;
  }

  @MessageMapping("/join-game_room/{topic}") // destination for a client
  @SendToUser("/queue/reply") // destination for a server
  public Object putUserIntoRoom(
      @DestinationVariable String topic,
      @Header("simpSessionAttributes") Map<String, Object> attributes ) {

    String username = (String) attributes.get("username");
    Long userId = (Long) attributes.get("userId");

    GameRoomDto roomDto = gameService.joinGameRoom(username, userId, topic);
    if (roomDto == null)
      return Map.of("InfoMessage","No available rooms found. Please create a new game.");

    return roomDto;
  }

//  @MessageMapping("/join-game_room") // destination for a client
//  @SendToUser("/queue/reply") // destination for a server
//  public Object putUserIntoRoom(
//      @Header("simpSessionAttributes") Map<String, Object> attributes ) {
//
//    String topic = "andrew";
//
//    String username = (String) attributes.get("username");
//    Long userId = (Long) attributes.get("userId");
//
//    GameRoomDto roomDto = gameService.joinGameRoom(username, userId, topic);
//    if (roomDto == null)
//      return Map.of("InfoMessage","No available rooms found. Please create a new game.");
//
//    return roomDto;
//  }



  @MessageMapping("/create-game_room/{qQuantity}/{topic}")
  @SendToUser("/queue/reply")
  public GameRoomDto createRoom(
      @Min(value = 2, message = "Minimum 5 questions")
      @Max(value = 15, message = "Maximum 15 questions")
      @DestinationVariable Integer qQuantity,
      @DestinationVariable String topic,
      @Header("simpSessionAttributes") Map<String, Object> attributes) {

    String username = (String) attributes.get("username");
    Long userId = (Long) attributes.get("userId");

    return gameService.createRoom(username, userId, qQuantity, topic);
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