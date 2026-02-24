package eugenestellar.model.dto;

import eugenestellar.model.gameEntity.GameRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class GameRoomDto {

  private String gameRoomTopic;
  private GameRoom gameRoom;

}