package eugenestellar.backendgame.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @AllArgsConstructor @Getter @Setter @NoArgsConstructor
@Table(name = "games_players")
public class GamePlayer {

  @EmbeddedId
  private GamePlayerComposedId id; // value of both keys

  @Column(name = "score", nullable = false)
  private int gameScore;

  @Column(nullable = false)
  private boolean status;

  @ManyToOne
  @MapsId("userId") // copy ID from the object userInfo to id.userId
  @JoinColumn(name = "user_id", nullable = false)
  private UserInfo userInfo;

  @ManyToOne
  @MapsId("gameId") // copy ID from the object game to id.gameId
  @JoinColumn(name = "game_id", nullable = false)
  private Game game;
}