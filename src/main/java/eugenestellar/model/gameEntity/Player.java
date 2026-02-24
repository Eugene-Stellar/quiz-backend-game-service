package eugenestellar.model.gameEntity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import eugenestellar.model.PlayerStatus;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Player {

  private String username;
  private Long id;
  private PlayerStatus status;

  private Integer score;
  private Boolean isAnswered;// = false;
  @JsonProperty("isCorrect")
  private Boolean isCurrentAnsCorrect;// = false;

  private Boolean isWinner;
}