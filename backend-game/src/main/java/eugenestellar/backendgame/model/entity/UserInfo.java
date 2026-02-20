package eugenestellar.backendgame.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity @Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Table(name = "users_info")
public class UserInfo {

  @Id
  @Column(name = "user_id", nullable = false, unique = true)
  private Long id;

  @Column(name = "username", nullable = false, unique = true)
  private String username;

  @Column(name = "game_quantity")
  private int gameQuantity;

  @Column(name = "total_score")
  private int totalScore;

  @Column(name = "wins")
  private int wins;
  @Column(name = "losses")
  private int losses;

  @OneToMany(mappedBy = "userInfo", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
  private List<GamePlayer> gamesPlayers = new ArrayList<>();
}