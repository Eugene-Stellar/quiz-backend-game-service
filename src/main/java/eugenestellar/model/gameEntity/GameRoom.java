package eugenestellar.model.gameEntity;

import eugenestellar.model.GameStatus;
import eugenestellar.model.dto.AnswerDto;
import jakarta.validation.constraints.Max;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder(toBuilder = true)
public class GameRoom {

  private String id;
  private GameStatus status;

  @Builder.Default
  private List<Player> players = new ArrayList<>();

  private Date countdownEndTime; // null if there's no timer

  private Integer qQuantity;

  @Max(value = 20, message = "Maximum 20 questions")
  private Integer currentQNum;

  private Long currentQId;
  private String currentQText;

  private List<Long> qIds = new ArrayList<>();
  private List<AnswerDto> currentAnswers = new ArrayList<>();
  private Date roundEndTime;

  private Date currentTime;
  private Long gameId; // for frontend to redirect user on game info page

  private String topic;
  private String currentImageUrl;
}