package eugenestellar.backendgame.model.gameEntity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import eugenestellar.backendgame.model.GameStatus;
import eugenestellar.backendgame.model.dto.AnswerDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameRoom {

  private String id;
  private GameStatus status;

  @Builder.Default
  private List<Player> players = new ArrayList<>();

  private Date countdownEndTime; // null if there's no timer

  @Min(value = 5, message = "Minimum 5 questions")
  @Max(value = 20, message = "Maximum 20 questions")
  @JsonProperty("qQuantity")
  private Integer qQuantity;

  @Max(value = 20, message = "Maximum 20 questions")
  private Integer currentQNum;

  private Long currentQId;
  private String currentQText;

  private List<Long> qIds = new ArrayList<>();
  private List<AnswerDto> currentAnswers = new ArrayList<>();
  private Date roundEndTime;

  private Date currentTime;
  private Long gameId;
}
