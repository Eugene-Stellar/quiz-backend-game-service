package eugenestellar.backendgame.service;

import eugenestellar.backendgame.model.GameStatus;
import eugenestellar.backendgame.model.PlayerStatus;
import eugenestellar.backendgame.model.dto.*;
import eugenestellar.backendgame.model.entity.*;
import eugenestellar.backendgame.model.gameEntity.GameRoom;
import eugenestellar.backendgame.model.gameEntity.Player;
import eugenestellar.backendgame.repository.GamePlayerRepo;
import eugenestellar.backendgame.repository.GameRepo;
import eugenestellar.backendgame.repository.QuestionRepo;
import eugenestellar.backendgame.repository.UserInfoRepo;
import jakarta.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

// TODO:
//  добавить темы для игр (то есть для вопросов)
//  фотки к вопросам
//  поле победителя отправлять владосу

@Slf4j
@Service
public class GameService {

  private static final int MAX_PLAYERS = 4;
  private static final int DEFAULT_QUESTION_QUANTITY = 2;

  private final SimpMessagingTemplate messagingTemplate;
  private final QuestionRepo questionRepo;
  private final GameRepo gameRepo;
  private final GamePlayerRepo gamePlayerRepo;
  private final UserInfoRepo userInfoRepo;
  private final Random random = new Random();

  // timers, ScheduledFuture<?> is a reference to method (e.g. expiredTimer()) i.e.
  // expiredTimer() executes only when it's time to do so
  private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
  private final TaskScheduler taskScheduler;
  private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();

  public GameService(GamePlayerRepo gamePlayerRepo,
                     SimpMessagingTemplate messagingTemplate,
                     QuestionRepo questionRepo, GameRepo gameRepo,
                     UserInfoRepo userInfoRepo,
                     TaskScheduler taskScheduler) {
    this.gamePlayerRepo = gamePlayerRepo;
    this.messagingTemplate = messagingTemplate;
    this.questionRepo = questionRepo;
    this.gameRepo = gameRepo;
    this.userInfoRepo = userInfoRepo;
    this.taskScheduler = taskScheduler;
  }

  public void startGameAndQuestionPropagation(GameRoom room) {

    // find random question Ids
    if (room.getQIds() == null || room.getQIds().isEmpty()) {
       List<Long> randomQIds = questionRepo.getRandomQuestionIds(room.getQQuantity());
       if (randomQIds.isEmpty())
         throw new RuntimeException("No questions in DB");
       room.setQIds(new ArrayList<>(randomQIds));
    }

    int currentQNum = (room.getCurrentQNum() == null || room.getCurrentQNum() == 0) ? 1
        : room.getCurrentQNum() + 1;
    // just in case
    if (currentQNum > room.getQIds().size()) {
      finishGame(room); return;
    }

    Long nextQId = room.getQIds().get(currentQNum - 1);
    Question q = questionRepo.findById(nextQId)
        .orElseThrow(() -> new RuntimeException("Question not found with id: " + nextQId));
    // set answers
    List<AnswerDto> answers = new ArrayList<>();
    for (Answer ans : q.getAnswers()) {
      answers.add(new AnswerDto(ans.getId(), ans.getText()));
    }

    Date current = Date.from(ZonedDateTime.now().toInstant());
    Date roundEnd = Date.from(ZonedDateTime.now().plusSeconds(10).toInstant());

    for (Player p : room.getPlayers()) {
      p.setIsAnswered(false);
      p.setIsCurrentAnsCorrect(false);
    }

    room.setCurrentTime(current);
    room.setRoundEndTime(roundEnd);

    room.setStatus(GameStatus.ACTIVE);
    room.setCurrentQId(q.getId());
    room.setCurrentQText(q.getText());
    room.setCurrentAnswers(answers);
    room.setCurrentQNum(currentQNum);

    sendUpdatedRoom(room);
// FIX 3: ОЧЕНЬ ВАЖНО! Обнуляем время в комнате после отправки.
    // Теперь следующие вызовы sendUpdatedRoom (при ответе игрока)
    // будут брать ZonedDateTime.now() внутри modifyRoomJson.
    room.setCurrentTime(null);

    ScheduledFuture<?> roundTask = taskScheduler.schedule(() -> getRoundResults(room), roundEnd);
    timers.put(room.getId(), roundTask);
  }

  // happens when round's countdown is finished or all players answered in advance
  @Transactional
  public void getRoundResults(GameRoom room) {



    Instant nextQ = ZonedDateTime.now().plusSeconds(15).toInstant();

    room.setStatus(GameStatus.ROUND_FINISHED);
    room.setRoundEndTime(Date.from(nextQ));
    room.setCurrentTime(Date.from(ZonedDateTime.now().toInstant()));

    sendUpdatedRoom(room);
    room.setCurrentTime(null); // Очистка

    for (Player p : room.getPlayers()) {
      p.setIsAnswered(false);
    }

    // the end of the last round -> the end of the game
    if (room.getCurrentQNum() >= room.getQQuantity()) {

      // find max score
      int maxScore = room.getPlayers().stream().
          mapToInt(p -> p.getScore() == null ? 0 : p.getScore())
          .max().orElse(0);

      // get a list of players with max score (processing a draw)
      List<Player> topPlayers = room.getPlayers().stream()
          .filter(p -> (p.getScore() == null ? 0  : p.getScore()) == maxScore)
          .toList();

      // select the winner (it's random if there are a few topPlayers)
      Player winner = topPlayers.isEmpty() ? null
          : topPlayers.get(random.nextInt(topPlayers.size()));

      Game game = new Game();
      game.setDate(new Date());
      game = gameRepo.save(game);
      room.setGameId(game.getId());

      for (Player p : room.getPlayers()) {
        Long playerId = p.getId();
        if (playerId == null) continue;
        boolean isWinner = winner != null && p.getId().equals(winner.getId());
        p.setIsWinner(isWinner);
        saveUserInDb(p, game, isWinner);
      }
      finishGame(room);
    } else {
      taskScheduler.schedule(() -> startGameAndQuestionPropagation(room), nextQ);
    }
  }

  @Transactional
  public void answerQ(Long qId, Long userId, Long answerId, String roomId) {
    GameRoom room = gameRooms.get(roomId);
    if (room == null) return;
    
    synchronized (room) {
      Player currentPlayer = room.getPlayers().stream()
          .filter(p -> p.getId().equals(userId))
          .findFirst()
          .orElseThrow(() -> new RuntimeException("Player with id: " + userId + " not found in room"));

      if (currentPlayer == null || currentPlayer.getIsAnswered()) return;

      Question q = questionRepo.findById(qId)
          .orElseThrow(() -> new RuntimeException("Question not found with id: " + qId));

      Answer selectedAnswer = q.getAnswers().stream()
          .filter(ans -> ans.getId().equals(answerId))
          .findFirst()
          .orElseThrow(() -> new RuntimeException("Answer not found with id: " + answerId));

      if (selectedAnswer.getIsCorrect()) {
        currentPlayer.setIsCurrentAnsCorrect(true);
        int currentScore = (currentPlayer.getScore() == null) ? 0
            : currentPlayer.getScore();
        currentPlayer.setScore(currentScore + 10);
      } else {
        currentPlayer.setIsCurrentAnsCorrect(false);
      }

      currentPlayer.setIsAnswered(true);

      long isAnsweredCount = room.getPlayers().stream()
          .filter(player -> player.getIsAnswered().equals(Boolean.TRUE)).count();

      // cancellation of round timer and propagate results
      if (isAnsweredCount == room.getPlayers().size()) {
        removeTimer(roomId);
        getRoundResults(room);
        return;
      }
      sendUpdatedRoom(room);
    }
  }


  private Optional<GameRoom> findGameRoomByPlayerId(long userId) {
    for (GameRoom gameRoom : gameRooms.values()) {
      for (Player player : gameRoom.getPlayers()) {
        if (player.getId().equals(userId)) {
          return Optional.of(gameRoom);
        }
      }
    }
    return Optional.empty();
  }

  // checking is there a room with a free capacity
  private Optional<GameRoom> findAvailableGameRoom() {
    for (GameRoom gameRoom : gameRooms.values()) {
      if ((gameRoom.getStatus() == GameStatus.WAITING || gameRoom.getStatus() == GameStatus.COUNTDOWN) &&
          gameRoom.getPlayers().size() < MAX_PLAYERS) {
        return Optional.of(gameRoom);
      }
    }
    return Optional.empty();
  }

  private void sendUpdatedRoom(GameRoom room) {
    GameRoom modifiedRoomJson = modifyRoomJson(room);
    String gameTopic = "/topic/game_room/" + room.getId();
    messagingTemplate.convertAndSend(gameTopic, modifiedRoomJson);
  }

  private GameRoom modifyRoomJson(GameRoom room) {
    GameRoom.GameRoomBuilder builder = room.toBuilder();

    Date timeStamp = (room.getCurrentTime() == null) ? Date.from(ZonedDateTime.now().toInstant())
      : room.getCurrentTime();
    List<Player> players = new ArrayList<>();
    List<Player> playersSnapshot = new ArrayList<>(room.getPlayers()); // protection from in-time changed Player List
    switch (room.getStatus()) {
      case WAITING:
        for (Player p : playersSnapshot) {
          players.add(p.toBuilder()
              .isAnswered(null)
              .isCurrentAnsCorrect(null)
              .isWinner(null)
              .score(null)
              .build());
        }
        builder.currentTime(null).gameId(null).players(players);
        break;
      case COUNTDOWN:
        builder
          .currentTime(timeStamp)
          .currentQText(null)
          .currentQId(null)
          .currentAnswers(null)
          .currentQNum(null)
          .qQuantity(null)
          .gameId(null)
          .roundEndTime(null).players(players);
        for (Player p : playersSnapshot) {
          players.add(p.toBuilder()
              .isAnswered(null)
              .isCurrentAnsCorrect(null)
              .isWinner(null)
              .score(null)
              .build());
        } break;
      case ACTIVE:
        builder
            .currentTime(timeStamp)
            .countdownEndTime(null).players(players).gameId(null);
        for (Player p : playersSnapshot) {
          int realScore = (p.getScore() == null) ? 0 : p.getScore();
          boolean isCorrect = Boolean.TRUE.equals(p.getIsCurrentAnsCorrect());
          players.add(p.toBuilder()
            .isCurrentAnsCorrect(null)
            .isWinner(null)
            .score(isCorrect ? realScore - 10 : realScore)
            .build());
          }
        break;
      case ROUND_FINISHED:
        builder.countdownEndTime(null).currentAnswers(null).currentQId(null).currentQText(null).qQuantity(null);
        builder.currentTime(timeStamp);
        for (Player p : playersSnapshot) {
          players.add(p.toBuilder()
              .isAnswered(null)
              .isWinner(null)
              .build());
        }
        builder.gameId(null).players(players).roundEndTime(room.getRoundEndTime());
        break;
      case FINISHED:
        for (Player p : playersSnapshot) {
          players.add(p.toBuilder()
              .isAnswered(null)
              .status(null)
              .isCurrentAnsCorrect(null)
              .build());
        }
        builder
          .currentQText(null)
          .currentQId(null)
          .currentAnswers(null)
          .currentQNum(null)
          .qQuantity(null)
          .roundEndTime(null)
          .countdownEndTime(null)
          .players(players)
          .roundEndTime(null); break;
    }
    builder.qIds(null);
    return builder.build();
  }

  private void removeTimer(String roomId) {
    ScheduledFuture<?> timer = timers.remove(roomId);
    // if there's a task cancel it i.e. cancel the timer
    if (timer != null) {
      timer.cancel(false);
    }
  }

  private void expiredTimer(GameRoom room) {
    // status changes from COUNTDOWN to ACTIVE if 4 players started at the same time (i.e. timer is deleted)
    if (room != null && room.getStatus() == GameStatus.COUNTDOWN) {
      timers.remove(room.getId());
      room.setCountdownEndTime(null);
      startGameAndQuestionPropagation(room);
    }
  }

  private void finishGame(GameRoom room) {

    String roomId = room.getId();
    removeTimer(roomId);
    room.setStatus(GameStatus.FINISHED);
    sendUpdatedRoom(room);

    Instant deleteTime = ZonedDateTime.now().plusSeconds(20).toInstant();
    taskScheduler.schedule(() ->
        gameRooms.remove(roomId), Date.from(deleteTime));
  }


  public void saveUserInDb(Player p, Game game, boolean isWinner) {
    Long playerId = p.getId();
    UserInfo userInfo = userInfoRepo.findById(p.getId()).orElseThrow(()
        -> new RuntimeException("There is no user with id = " + playerId));

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
    gamePlayer.setStatus(isWinner); // win

    gamePlayerRepo.save(gamePlayer);
  }

  // DELETE user from game room
  @Transactional
  public synchronized void leaveGameRoom(Long userId, String roomId) {

    GameRoom room = gameRooms.get(roomId);
    if (room == null) {return;}

    if (room.getStatus() == GameStatus.FINISHED) {
      room.getPlayers().removeIf(player -> player.getId().equals(userId));
      return;
    }
    room.getPlayers().removeIf(player -> player.getId().equals(userId));

    // TECHNICAL WIN
    if (room.getPlayers().size() == 1 && room.getStatus() == GameStatus.ACTIVE) {
      Player winner = room.getPlayers().get(0);

      Game game = new Game();
      game.setDate(new Date());
      game = gameRepo.save(game);
      room.setGameId(game.getId());
      winner.setIsWinner(true);

      saveUserInDb(winner, game,true);
      finishGame(room);
      return;
    }

    // delete room if it's empty
    if (room.getPlayers().isEmpty()) {
      if (room.getStatus() == GameStatus.COUNTDOWN) {
        removeTimer(roomId);
      }
      gameRooms.remove(roomId);
      return;
    }
    // change status of game room if there's 1 player and
    // status of the room is COUNTDOWN
    if (room.getPlayers().size() < 2 &&
        room.getStatus() == GameStatus.COUNTDOWN) {
      removeTimer(roomId);
      room.setCountdownEndTime(null);
      room.setStatus(GameStatus.WAITING);
    }
    sendUpdatedRoom(room);
  }

  private void deleteRoomIfAllDisconnected(String roomId) {
    GameRoom room = gameRooms.get(roomId);
    if (room == null) { return; } // if already deleted

    boolean hasConnectedPlayer = room.getPlayers().stream()
        .anyMatch(player -> player.getStatus() == PlayerStatus.CONNECTED);

    // if there's one connected user then game continues otherwise
    // all users are disconnected then room and timer are deleted
    if (!hasConnectedPlayer) {
      gameRooms.remove(roomId);
      removeTimer(roomId);
      log.info("GameRoom {} deleted due to inactivity", roomId);
    }
  }


  // change status if user disconnected i.e. closed a tab or browser
  public synchronized void disconnectGameRoom(Long userId) {
    Optional<GameRoom> roomOpt = findGameRoomByPlayerId(userId);

    if (roomOpt.isPresent()) {
      GameRoom room = roomOpt.get();

      if (room.getStatus() == GameStatus.FINISHED) {
        room.getPlayers().removeIf(p -> p.getId().equals(userId));
        if (room.getPlayers().isEmpty()) {
          gameRooms.remove(room.getId());
        }
        return;
      }

      Player targetPlayer = null;
      for (Player p : room.getPlayers()) {
        if (p.getId().equals(userId)) {
          targetPlayer = p;
        }
      }
      if (targetPlayer == null) {return;}

      if (room.getStatus() == GameStatus.ACTIVE || room.getStatus() == GameStatus.ROUND_FINISHED) {
        targetPlayer.setStatus(PlayerStatus.DISCONNECTED);
      } else { // if room status COUNTDOWN or WAITING then player is removed
        room.getPlayers().remove(targetPlayer);
        // if room is empty then delete it
        if (room.getPlayers().isEmpty()) {
          String roomId = room.getId();
          removeTimer(room.getId());
          gameRooms.remove(roomId);
        }
      }


      long connectedPlayersCount = room.getPlayers().stream()
          .filter(player -> player.getStatus() == PlayerStatus.CONNECTED)
          .count();
      // if there's only one player and RoomStatus is COUNTDOWN then status changes to WAITING
      // and restart countdown only when there are two Connected users
      String roomId = room.getId();
      if (connectedPlayersCount < 2 && room.getStatus() == GameStatus.COUNTDOWN) {
        removeTimer(roomId);
        room.setCountdownEndTime(null);
        room.setStatus(GameStatus.WAITING);
      }

      // if everyone in GameRoom has status DISCONNECTED -> room is deleted in 90 seconds if no one is connected
      if (connectedPlayersCount == 0 && room.getStatus() == GameStatus.ACTIVE) {
        Instant deleteTime = ZonedDateTime.now().plusSeconds(10).toInstant();
        try {
          taskScheduler.schedule(() -> deleteRoomIfAllDisconnected(roomId), deleteTime);
        } catch (TaskRejectedException e) {
          log.debug("Server is being deleted, scheduled deletion of the room {} cancelled", roomId);
        }
      }
      sendUpdatedRoom(room);
    }
  }

  public String getPlayerState(Long userId) {
    Optional<GameRoom> gameRoomOpt = findGameRoomByPlayerId(userId);
    if (gameRoomOpt.isEmpty()) {
      return PlayerStatus.NOT_IN_GAME.toString();
    }

    for (Player player : gameRoomOpt.get().getPlayers()) {
      if (player.getId().equals(userId)) {
        return player.getStatus().toString();
      }
    }
    // just in case
    return PlayerStatus.NOT_IN_GAME.toString();
  }



  public synchronized GameRoomDto joinGameRoom(String username, Long userId) {
    // checking whether any room contains the user or not,
    // if yes then reconnect happens i.e. return the user in that room and change its status on CONNECTED
    //
    // if user does not belong any room, then user is placed in a room with a free capacity or creating a new one

    GameRoom targetRoom;
    // RECONNECT
    Optional<GameRoom> existingGameRoomOpt = findGameRoomByPlayerId(userId);
    if (existingGameRoomOpt.isPresent()) {
      targetRoom = existingGameRoomOpt.get();
      for (Player p : targetRoom.getPlayers()) {
        if (p.getId().equals(userId)) {
          p.setStatus(PlayerStatus.CONNECTED);
          break;
        }
      }

      // sending to all players an updated list of players
      sendUpdatedRoom(targetRoom);
      String gameTopic = "/topic/game_room/" + targetRoom.getId();

      // return topic (of this room) to this user and the list of players in the room
      return new GameRoomDto(gameTopic, modifyRoomJson(targetRoom));
    }
    /* JOIN GameRoom or CREAT a default one */
    Optional<GameRoom> optionalGameRoom = findAvailableGameRoom();
    // A Short way: GameRoom targetGameRoom = findAvailableGameRoom()
    //    .orElseGet(this::createGameRoom);
    if (optionalGameRoom.isPresent()) {
      targetRoom = optionalGameRoom.get();
    } else {
      targetRoom = createNewGameRoom(DEFAULT_QUESTION_QUANTITY);
    }
    return processPlayerJoin(targetRoom, username, userId);
  }

  private GameRoomDto processPlayerJoin(GameRoom targetRoom, String username, Long userId) {

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

        // timer propagation
        Instant endTimeInst = ZonedDateTime.now().plusSeconds(10).toInstant();
        Date endTime = Date.from(endTimeInst);
        targetRoom.setCountdownEndTime(endTime);

        ScheduledFuture<?> timerTask = taskScheduler
            .schedule(() -> expiredTimer(targetRoom), endTimeInst);

        timers.put(targetRoom.getId(), timerTask);
      }

      String gameTopic = "/topic/game_room/" + targetRoom.getId();
      // 4 connected players -> countdown finished and start game
      if (connectedPlayersCount == MAX_PLAYERS) {
        removeTimer(targetRoom.getId());
        targetRoom.setCountdownEndTime(null);
        // start game & send the 1st question
        startGameAndQuestionPropagation(targetRoom);
        return new GameRoomDto(gameTopic, modifyRoomJson(targetRoom));
      }

      // sending to all players an updated list of players
      sendUpdatedRoom(targetRoom);
      // sending the topic(of this room) to client so that he could subscribe on it
      // and the list of players in the room
      return new GameRoomDto(gameTopic, modifyRoomJson(targetRoom));
  }

  // creating a brand new GameRoom
  public GameRoom createNewGameRoom(int qQuantity) {
    String gameRoomId;
    do {
      gameRoomId = UUID.randomUUID().toString();
    }
    while (gameRooms.containsKey(gameRoomId));

    GameRoom gameRoom = GameRoom.builder()
        .id(gameRoomId)
        .status(GameStatus.WAITING)
        .qQuantity(qQuantity)
        .players(new CopyOnWriteArrayList<>())
        .build();
    gameRooms.put(gameRoomId, gameRoom);
    return gameRoom;
  }

  public synchronized GameRoomDto createRoom(String username, Long userId, Integer qQuantity) {
    Optional<GameRoom> room = findGameRoomByPlayerId(userId);

    room.ifPresent(gameRoom -> leaveGameRoom(userId, gameRoom.getId()));

    int finalQQuantity = (qQuantity == null || qQuantity < 5) ? DEFAULT_QUESTION_QUANTITY
        : qQuantity;
    GameRoom newRoom = createNewGameRoom(finalQQuantity);

    return processPlayerJoin(newRoom, username, userId);
  }
}