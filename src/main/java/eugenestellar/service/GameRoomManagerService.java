package eugenestellar.service;

import eugenestellar.model.GameStatus;
import eugenestellar.model.gameEntity.GameRoom;
import eugenestellar.model.gameEntity.Player;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static eugenestellar.service.GameService.MAX_PLAYERS;

@Slf4j
@Component
public class GameRoomManagerService {

  // timers, ScheduledFuture<?> is a reference to method(e.g. expiredTimer()) i.e.
  // expiredTimer() executes only when it's time to do so
  private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
  private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

  public void addRoom (GameRoom room) {
    gameRooms.put(room.getId(), room);
  }

  public GameRoom getRoom (String roomId) {
    return gameRooms.get(roomId);
  }

  public void removeRoom (String roomId) {
    gameRooms.remove(roomId);
    removeTimer(roomId);
    log.info("Room with id: {} was deleted successfully.", roomId);
  }

  public boolean containsRoomWithId(String roomId ) {
    return gameRooms.containsKey(roomId);
  }

  public void addTimer(String roomId, ScheduledFuture<?> task) {
    timers.put(roomId, task);
  }

  public void removeTimer(String roomId) {
    ScheduledFuture<?> timer = timers.remove(roomId);
    // if there's a task cancel it i.e. cancel the timer
    if (timer != null) {
      timer.cancel(true);
    }
  }

  public Optional<GameRoom> findGameRoomByPlayerId(long userId) {
    for (GameRoom gameRoom : gameRooms.values()) {
      for (Player player : gameRoom.getPlayers()) {
        if (player.getId().equals(userId)) {
          return Optional.of(gameRoom);
        }
      }
    }
    return Optional.empty();
  }

  public Optional<GameRoom> findAvailableGameRoom(String qTopic, int qQuantity) {
    boolean isRandom = "random".equals(qTopic);

    for (GameRoom gameRoom : gameRooms.values()) {
      boolean specifyTopic = isRandom || gameRoom.getTopic().equals(qTopic);

      if ((gameRoom.getStatus() == GameStatus.WAITING ||
          gameRoom.getStatus() == GameStatus.COUNTDOWN) &&
          gameRoom.getPlayers().size() < MAX_PLAYERS &&
          specifyTopic && gameRoom.getQQuantity() == qQuantity) {
        return Optional.of(gameRoom);
      }
    }
    return Optional.empty();
  }
}