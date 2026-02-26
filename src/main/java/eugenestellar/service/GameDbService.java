package eugenestellar.service;

import eugenestellar.model.entity.Game;
import eugenestellar.model.entity.GamePlayer;
import eugenestellar.model.entity.GamePlayerComposedId;
import eugenestellar.model.entity.UserInfo;
import eugenestellar.model.gameEntity.GameRoom;
import eugenestellar.model.gameEntity.Player;
import eugenestellar.repository.GamePlayerRepo;
import eugenestellar.repository.GameRepo;
import eugenestellar.repository.UserInfoRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class GameDbService {

  private final GameRepo gameRepo;
  private final UserInfoRepo userInfoRepo;
  private final GamePlayerRepo gamePlayerRepo;

  public GameDbService(GamePlayerRepo gamePlayerRepo, GameRepo gameRepo, UserInfoRepo userInfoRepo) {
    this.gamePlayerRepo = gamePlayerRepo;
    this.gameRepo = gameRepo;
    this.userInfoRepo = userInfoRepo;
  }

  @Transactional
  public void saveFinalResultsToDb(GameRoom room) {
    Game game = createAndSaveGame(room);
    // save every user in db
    for (Player p : room.getPlayers()) {
      saveUserToDb(p, game, p.getIsWinner());
    }
  }

  private void saveUserToDb(Player p, Game game, boolean isWinner) {
    Long playerId = p.getId();
    if (playerId == null) return;
    UserInfo userInfo = userInfoRepo.findById(p.getId()).orElseThrow(()
        -> new RuntimeException("There is no user with id = " + playerId));

    // update UserInfo and GamePlayers
    userInfo.setGameQuantity(userInfo.getGameQuantity() + 1);
    if (p.getScore() != null)
      userInfo.setTotalScore(userInfo.getTotalScore() + p.getScore());

    GamePlayer gamePlayer = new GamePlayer();
    gamePlayer.setId(new GamePlayerComposedId(playerId, game.getId()));
    if (p.getScore() != null)
      gamePlayer.setGameScore(p.getScore());

    if (isWinner) {
      userInfo.setWins(userInfo.getWins() + 1);
    } else {
      userInfo.setLosses(userInfo.getLosses() + 1);
    }
    userInfoRepo.save(userInfo);

    gamePlayer.setGame(game);
    gamePlayer.setUserInfo(userInfo);
    gamePlayer.setStatus(isWinner);

    gamePlayerRepo.save(gamePlayer);
  }

  @Transactional
  public void technicalWin(Player winner, GameRoom room) {
    Game game = createAndSaveGame(room);
    saveUserToDb(winner, game,true);
  }

  private Game createAndSaveGame (GameRoom room) {
    Game game = new Game();
    game.setDate(new Date());
    game = gameRepo.save(game);
    room.setGameId(game.getId());

    return game;
  }
}