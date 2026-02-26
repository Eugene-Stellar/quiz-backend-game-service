package eugenestellar.service;

import eugenestellar.model.GameStatus;
import eugenestellar.model.PlayerStatus;
import eugenestellar.model.dto.GameRoomResponse;
import eugenestellar.model.gameEntity.GameRoom;
import eugenestellar.model.gameEntity.Player;
import eugenestellar.model.dto.mapper.GameMapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
public class GameService {

  private static final int WAITING_COUNTDOWN_TIME = 5;
  private static final int ROOM_DELETION_TIME = 30;
  public static final int MAX_PLAYERS = 4;

  private final GameRoundService gameRoundService;
  private final QuestionService questionService;
  private final GameDbService gameDbService;
  private final GameRoomManager gameRoomManager;
  private final GameNotificationService notificationService;

  private final Random random = new Random();
  private final TaskScheduler taskScheduler;

  public GameService(GameNotificationService notificationService,
                     QuestionService questionService,
                     TaskScheduler taskScheduler,
                     GameRoundService gameRoundService,
                     GameDbService gameDbService,
                     GameRoomManager gameRoomManager) {
    this.notificationService = notificationService;
    this.taskScheduler = taskScheduler;
    this.gameDbService = gameDbService;
    this.gameRoomManager = gameRoomManager;
    this.gameRoundService = gameRoundService;
    this.questionService = questionService;
  }

  public String getPlayerState(Long userId) {
    Optional<GameRoom> gameRoomOpt = gameRoomManager.findGameRoomByPlayerId(userId);
    if (gameRoomOpt.isEmpty()) {
      return PlayerStatus.NOT_IN_GAME.toString();
    }

    GameRoom room = gameRoomOpt.get();
    if (room.getStatus() == GameStatus.FINISHED) {
      return PlayerStatus.NOT_IN_GAME.toString();
    }

    for (Player player : room.getPlayers()) {
      if (player.getId().equals(userId)) {
        return player.getStatus().toString();
      }
    }
    // just in case
    return PlayerStatus.NOT_IN_GAME.toString();
  }

  public GameRoomResponse joinGameRoom(String username, Long userId, String qTopic, int qQuantity) {
    // checking whether any room contains user or not,
    // if yes, then reconnect happens i.e. return the user in that room and change its status on CONNECTED.
    // if no, then user is placed in available room with corresponding topic and question quantity

    if (!"random".equals(qTopic))
      questionService.validateTopic(qTopic);
    GameRoom targetRoom;
    // RECONNECT
    Optional<GameRoom> existingGameRoomOpt = gameRoomManager.findGameRoomByPlayerId(userId);
    if (existingGameRoomOpt.isPresent()) {
      targetRoom = existingGameRoomOpt.get();
      synchronized (targetRoom) {
        for (Player p : targetRoom.getPlayers()) {
          if (p.getId().equals(userId)) {
            p.setStatus(PlayerStatus.CONNECTED);
            break;
          }
        }
      }

      // sending to all players an updated list of players
      notificationService.sendUpdatedRoom(targetRoom);
      String gameTopic = "/topic/game_room/" + targetRoom.getId();

      // return topic (of this room) to this user and the list of players in the room
      return new GameRoomResponse(gameTopic, GameMapper.toDto(targetRoom));
    }
    /* JOIN AVAILABLE ROOM */
    Optional<GameRoom> optionalGameRoom = gameRoomManager.findAvailableGameRoom(qTopic, qQuantity);
    if (optionalGameRoom.isPresent()) {
      targetRoom = optionalGameRoom.get();
    } else {
      return createRoom(username, userId, qQuantity, qTopic);
    }
    return processPlayerJoin(targetRoom, username, userId);
  }

  private GameRoomResponse processPlayerJoin(GameRoom targetRoom, String username, Long userId) {
    String roomId = targetRoom.getId();
    String gameTopic = "/topic/game_room/" + roomId;

    synchronized (targetRoom) {
      if (targetRoom.getPlayers().size() >= MAX_PLAYERS) {
        return joinGameRoom(username, userId, targetRoom.getTopic(), targetRoom.getQQuantity());
      }

      Player playerToRoom = Player.builder()
          .username(username)
          .id(userId)
          .status(PlayerStatus.CONNECTED)
          .score(0)
          .isAnswered(false)
          .build();
      targetRoom.getPlayers().add(playerToRoom);

      long connectedPlayersCount = targetRoom.getPlayers().stream()
          .filter(p -> p.getStatus() == PlayerStatus.CONNECTED)
          .count();

      // 2 connected players -> start timer
      if (connectedPlayersCount >= 2 && targetRoom.getStatus() == GameStatus.WAITING) {
        targetRoom.setStatus(GameStatus.COUNTDOWN);

        Instant endTimeInst = ZonedDateTime.now().plusSeconds(WAITING_COUNTDOWN_TIME).toInstant();
        Date endTime = Date.from(endTimeInst);
        targetRoom.setCountdownEndTime(endTime);

        // schedule timer deletion
        ScheduledFuture<?> timerTask = taskScheduler
            .schedule(() -> expireWaitingCountdown(targetRoom), endTimeInst);

        gameRoomManager.addTimer(roomId, timerTask);
      }

      // 4 connected players -> countdown finished & start game
      if (connectedPlayersCount == MAX_PLAYERS) {
        gameRoomManager.removeTimer(roomId);
        targetRoom.setCountdownEndTime(null);

        // start game & send the 1st question
        gameRoundService.startGameAndPropagateQuestions(targetRoom);

        return new GameRoomResponse(gameTopic, GameMapper.toDto(targetRoom));
      }
    }

    // sending to all players an updated list of players
    notificationService.sendUpdatedRoom(targetRoom);
    // send topic (of this room) to client so that he could subscribe on it
    return new GameRoomResponse(gameTopic, GameMapper.toDto(targetRoom));
  }

  private void expireWaitingCountdown(GameRoom room) {
    // status changes from COUNTDOWN to ACTIVE, when waiting countdown time is finished
    if (room != null && room.getStatus() == GameStatus.COUNTDOWN) {
      gameRoomManager.removeTimer(room.getId());
      room.setCountdownEndTime(null);
      gameRoundService.startGameAndPropagateQuestions(room);
    }
  }

  // creating a brand new GameRoom
  private GameRoomResponse createRoom(String username, Long userId, Integer qQuantity, String qTopic) {
    if (qTopic.equals("random")) {
      List<String> topics = questionService.findAllTopics();
      qTopic = topics.get(random.nextInt(topics.size()));
    } else {
      questionService.validateTopic(qTopic);
    }

    // check is there user in any room if yes then delete him from that room
    Optional<GameRoom> room = gameRoomManager.findGameRoomByPlayerId(userId);
    room.ifPresent(gameRoom -> leaveGameRoom(userId, gameRoom.getId()));

    String gameRoomId;
    do {
      gameRoomId = UUID.randomUUID().toString();
    }
    while (gameRoomManager.containsRoomWithId(gameRoomId));

    GameRoom newRoom = GameRoom.builder()
        .id(gameRoomId)
        .status(GameStatus.WAITING)
        .qQuantity(qQuantity)
        .players(new CopyOnWriteArrayList<>())
        .topic(qTopic)
        .build();
    gameRoomManager.addRoom(newRoom);
    return processPlayerJoin(newRoom, username, userId);
  }

  // DELETE user from game room
  public void leaveGameRoom(Long userId, String roomId) {

    GameRoom room = gameRoomManager.getRoom(roomId);
    if (room == null) {return;}

    synchronized (room) {
      // in the case if player started new game and still hangs in finished room which will be deleted in 3 seconds
      if (room.getStatus() == GameStatus.FINISHED) {
        return;
      }

      // delete player from room
      room.getPlayers().removeIf(player -> player.getId().equals(userId));

      // TECHNICAL WIN if one player is remained in active room
      if (room.getPlayers().size() == 1 && (room.getStatus() == GameStatus.ACTIVE || room.getStatus() == GameStatus.ROUND_FINISHED)) {
        Player winner = room.getPlayers().getFirst();
        winner.setIsWinner(true);

        gameDbService.technicalWin(winner, room);
        gameRoundService.finishGame(room);
        return;
      }

      // delete room if it's empty
      if (room.getPlayers().isEmpty()) {
        gameRoomManager.removeRoom(roomId);
        return;
      }
      // change status of room if there's 1 player and
      // status of the room is COUNTDOWN
      if (room.getPlayers().size() < 2 &&
          room.getStatus() == GameStatus.COUNTDOWN) {
        gameRoomManager.removeTimer(roomId);
        room.setCountdownEndTime(null);
        room.setStatus(GameStatus.WAITING);
      }
    }
    notificationService.sendUpdatedRoom(room);
  }

  private void deleteRoomIfAllDisconnected(GameRoom room) {
    if (room == null) { return; } // if already deleted

    String roomId = room.getId();
    boolean hasConnectedPlayer = room.getPlayers().stream()
        .anyMatch(player -> player.getStatus() == PlayerStatus.CONNECTED);

    // if there's one connected user then game continues otherwise
    // all users are disconnected then room and timer are deleted
    if (!hasConnectedPlayer) {
      gameRoomManager.removeRoom(roomId);
      log.info("GameRoom {} deleted due to inactivity", roomId);
    }
  }

  // case of disconnection (i.e. closed a tab or browser)
  public void disconnectGameRoom(Long userId) {
    Optional<GameRoom> roomOpt = gameRoomManager.findGameRoomByPlayerId(userId);

    if (roomOpt.isPresent()) {
      GameRoom room = roomOpt.get();
      String roomId = room.getId();

      synchronized (room) {
        // return, because room will be deleted anyway, since it's finished
        if (room.getStatus() == GameStatus.FINISHED) {
          return;
        }

        Player targetPlayer = null;
        for (Player p : room.getPlayers()) {
          if (p.getId().equals(userId)) {
            targetPlayer = p;
          }
        }
        if (targetPlayer == null) {return;}

        // change player status if user's disconnected from active room
        if (room.getStatus() == GameStatus.ACTIVE || room.getStatus() == GameStatus.ROUND_FINISHED) {
          targetPlayer.setStatus(PlayerStatus.DISCONNECTED);
        } else { // if room status COUNTDOWN or WAITING then player is removed
          room.getPlayers().remove(targetPlayer);
          // if room is empty then delete it
          if (room.getPlayers().isEmpty()) {
            gameRoomManager.removeRoom(roomId);
            return;
          }
        }

        long connectedPlayersCount = room.getPlayers().stream()
            .filter(player -> player.getStatus() == PlayerStatus.CONNECTED)
            .count();

        // if there's only one player and RoomStatus is COUNTDOWN then status changes to WAITING and remove timer
        if (connectedPlayersCount < 2 && room.getStatus() == GameStatus.COUNTDOWN) {
          gameRoomManager.removeTimer(roomId);
          room.setCountdownEndTime(null);
          room.setStatus(GameStatus.WAITING);
        }

        // if everyone in GameRoom has status DISCONNECTED -> room is deleted in 30 seconds if no one is connected
        if (connectedPlayersCount == 0 && (room.getStatus() == GameStatus.ACTIVE ||
            room.getStatus() == GameStatus.ROUND_FINISHED)) {
          Instant deleteTime = ZonedDateTime.now().plusSeconds(ROOM_DELETION_TIME).toInstant();
          try {
            taskScheduler.schedule(() -> deleteRoomIfAllDisconnected(room), deleteTime);
          } catch (TaskRejectedException e) {
            log.info("Server is shutting down, scheduled deletion of the room {} cancelled", roomId);
          }
        }
      }
      notificationService.sendUpdatedRoom(room);
    }
  }
}