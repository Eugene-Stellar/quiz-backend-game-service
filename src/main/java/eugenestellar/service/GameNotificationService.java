package eugenestellar.service;

import eugenestellar.model.dto.GameRoomDto;
import eugenestellar.model.dto.mapper.GameMapper;
import eugenestellar.model.gameEntity.GameRoom;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class GameNotificationService {

  private final SimpMessagingTemplate messagingTemplate;

  public GameNotificationService(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void sendUpdatedRoom(GameRoom room) {
    GameRoomDto gameRoomDto = GameMapper.toDto(room);
    String gameTopic = "/topic/game_room/" + room.getId();
    messagingTemplate.convertAndSend(gameTopic, gameRoomDto);
  }

}