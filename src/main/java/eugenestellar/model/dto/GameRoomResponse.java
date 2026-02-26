package eugenestellar.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class GameRoomResponse {

  private String gameRoomTopic;
  private GameRoomDto gameRoom;

}