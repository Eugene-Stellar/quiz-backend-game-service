package eugenestellar.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import eugenestellar.model.GameStatus;
import eugenestellar.model.gameEntity.GameRoom;
import eugenestellar.model.gameEntity.Player;
import jakarta.validation.constraints.Max;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Setter @Getter @AllArgsConstructor @NoArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameRoomDto {

  private String id;
  private GameStatus status;

  @Builder.Default
  private List<Player> players = new ArrayList<>();

  private Date countdownEndTime; // null if there's no timer

  @JsonProperty("qQuantity")
  private Integer qQuantity;

  @Max(value = 20, message = "Maximum 20 questions")
  private Integer currentQNum;

  private Long currentQId;
  private String currentQText;

  private List<AnswerDto> currentAnswers = new ArrayList<>();
  private Date roundEndTime;

  private Date currentTime;
  private Long gameId; // for frontend to redirect user on game info page

  private String topic;
  private String currentImageUrl;
}