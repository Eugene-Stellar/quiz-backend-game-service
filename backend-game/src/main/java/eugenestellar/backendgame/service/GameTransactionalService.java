////package eugenestellar.backendgame.service;
////
////import eugenestellar.backendgame.model.entity.Game;
////import eugenestellar.backendgame.model.entity.GamePlayer;
////import eugenestellar.backendgame.model.entity.GamePlayerComposedId;
////import eugenestellar.backendgame.model.entity.UserInfo;
////import eugenestellar.backendgame.model.gameEntity.Player;
////import eugenestellar.backendgame.repository.GamePlayerRepo;
////import eugenestellar.backendgame.repository.GameRepo;
////import eugenestellar.backendgame.repository.UserInfoRepo;
////import jakarta.transaction.Transactional;
////import org.springframework.stereotype.Service;
////
////import java.util.Date;
////import java.util.List;
////
////@Service
////public class GameTransactionalService {
////
////  private final UserInfoRepo userInfoRepo;
////  private final GamePlayerRepo gamePlayerRepo;
////  private final GameRepo gameRepo;
////
////  public GameTransactionalService(GamePlayerRepo gamePlayerRepo, UserInfoRepo userInfoRepo, GameRepo gameRepo) {
////    this.gamePlayerRepo = gamePlayerRepo;
////    this.userInfoRepo = userInfoRepo;
////    this.gameRepo = gameRepo;
////  }
////
////  @Transactional
////  public void saveGameResults(List<Player> players, Player winner) {
////    Game game = new Game();
////    game.setDate(new Date());
////    game = gameRepo.save(game);
////
////    for (Player p : players) {
////      if (p.getId() == null) continue;
////      boolean isWinner = winner != null && p.getId().equals(winner.getId());
////      savePlayerResult(p, game, isWinner);
////    }
////  }
////
////  private void savePlayerResult(Player p, Game game, boolean isWinner) {
////    Long playerId = p.getId();
////    UserInfo userInfo = userInfoRepo.findById(playerId).orElseThrow(()
////        -> new RuntimeException("There is no user with id = " + playerId));
////
////    userInfo.setGameQuantity(userInfo.getGameQuantity() + 1);
////    if (p.getScore() != null)
////      userInfo.setTotalScore(userInfo.getTotalScore() + p.getScore());
////
////    if (isWinner) {
////      userInfo.setWins(userInfo.getWins() + 1);
////    }
////    userInfoRepo.save(userInfo);
////
////
////    GamePlayer gamePlayer = new GamePlayer();
////    gamePlayer.setId(new GamePlayerComposedId(playerId, game.getId()));
////    gamePlayer.setGame(game);
////    gamePlayer.setUserInfo(userInfo);
////    gamePlayer.setStatus(isWinner); // win
////    if (p.getScore() != null)
////      gamePlayer.setGameScore(p.getScore());
////
////    gamePlayerRepo.save(gamePlayer);
////  }
////}
//
//package eugenestellar.backendgame.service;
//
//import eugenestellar.backendgame.model.entity.Game;
//import eugenestellar.backendgame.model.entity.GamePlayer;
//import eugenestellar.backendgame.model.entity.GamePlayerComposedId;
//import eugenestellar.backendgame.model.entity.UserInfo;
//import eugenestellar.backendgame.model.gameEntity.Player;
//import eugenestellar.backendgame.repository.GamePlayerRepo;
//import eugenestellar.backendgame.repository.GameRepo;
//import eugenestellar.backendgame.repository.UserInfoRepo;
//import jakarta.transaction.Transactional;
//import lombok.extern.slf4j.Slf4j;  // ← ДОБАВИТЬ
//import org.springframework.stereotype.Service;
//
//import java.util.Date;
//import java.util.List;
//
//@Slf4j  // ← ДОБАВИТЬ
//@Service
//public class GameTransactionalService {
//
//  private final UserInfoRepo userInfoRepo;
//  private final GamePlayerRepo gamePlayerRepo;
//  private final GameRepo gameRepo;
//
//  public GameTransactionalService(GamePlayerRepo gamePlayerRepo, UserInfoRepo userInfoRepo, GameRepo gameRepo) {
//    this.gamePlayerRepo = gamePlayerRepo;
//    this.userInfoRepo = userInfoRepo;
//    this.gameRepo = gameRepo;
//  }
//
//  @Transactional
//  public void saveGameResults(List<Player> players, Player winner) {
//    log.info("=== START saveGameResults ===");  // ← ДОБАВИТЬ
//    log.info("Players count: {}, Winner: {}", players.size(),   // ← ДОБАВИТЬ
//        winner != null ? winner.getId() : "null");  // ← ДОБАВИТЬ
//
//    Game game = new Game();
//    game.setDate(new Date());
//    game = gameRepo.save(game);
//    log.info("Game saved with ID: {}", game.getId());  // ← ДОБАВИТЬ
//
//    for (Player p : players) {
//      if (p.getId() == null) {
//        log.warn("Skipping player with null ID");  // ← ДОБАВИТЬ
//        continue;
//      }
//      boolean isWinner = winner != null && p.getId().equals(winner.getId());
//      log.info("Saving player: ID={}, Score={}, IsWinner={}",   // ← ДОБАВИТЬ
//          p.getId(), p.getScore(), isWinner);  // ← ДОБАВИТЬ
//      savePlayerResult(p, game, isWinner);
//    }
//    log.info("=== END saveGameResults ===");  // ← ДОБАВИТЬ
//  }
//
//  private void savePlayerResult(Player p, Game game, boolean isWinner) {
//    Long playerId = p.getId();
//    log.info("  -> savePlayerResult for player {}", playerId);  // ← ДОБАВИТЬ
//
//    UserInfo userInfo = userInfoRepo.findById(playerId).orElseThrow(()
//        -> new RuntimeException("There is no user with id = " + playerId));
//
//    log.info("  -> UserInfo before: games={}, score={}, wins={}",   // ← ДОБАВИТЬ
//        userInfo.getGameQuantity(), userInfo.getTotalScore(), userInfo.getWins());  // ← ДОБАВИТЬ
//
//    userInfo.setGameQuantity(userInfo.getGameQuantity() + 1);
//    if (p.getScore() != null)
//      userInfo.setTotalScore(userInfo.getTotalScore() + p.getScore());
//
//    if (isWinner) {
//      userInfo.setWins(userInfo.getWins() + 1);
//    }
//    userInfoRepo.save(userInfo);
//    log.info("  -> UserInfo after: games={}, score={}, wins={}",   // ← ДОБАВИТЬ
//        userInfo.getGameQuantity(), userInfo.getTotalScore(), userInfo.getWins());  // ← ДОБАВИТЬ
//
//    GamePlayer gamePlayer = new GamePlayer();
//    gamePlayer.setId(new GamePlayerComposedId(playerId, game.getId()));
//    gamePlayer.setGame(game);
//    gamePlayer.setUserInfo(userInfo);
//    gamePlayer.setStatus(isWinner);
//    if (p.getScore() != null)
//      gamePlayer.setGameScore(p.getScore());
//
//    gamePlayerRepo.save(gamePlayer);
//    log.info("  -> GamePlayer saved: gameId={}, playerId={}, score={}",   // ← ДОБАВИТЬ
//        game.getId(), playerId, gamePlayer.getGameScore());  // ← ДОБАВИТЬ
//  }
//}