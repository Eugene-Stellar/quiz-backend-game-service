package eugenestellar.service;

import eugenestellar.exception.ws.FrontendException;
import eugenestellar.model.GameStatus;
import eugenestellar.model.PlayerStatus;
import eugenestellar.model.dto.GameRoomResponse;
import eugenestellar.model.entity.*;
import eugenestellar.model.dto.AnswerDto;
import eugenestellar.model.gameEntity.GameRoom;
import eugenestellar.model.gameEntity.Player;
import eugenestellar.repository.QuestionRepo;
import eugenestellar.model.dto.mapper.GameMapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

// TODO: установить время для соединения (удалять через 1-2 часа), то есть рвать соединение
@Slf4j
@Service
public class GameService {

  // TIMING
  private static final int ROUND_TIME = 20;
  private static final int ROUND_RESULTS_TIME = 5;
  private static final int WAITING_COUNTDOWN_TIME = 5;

  public static final int MAX_PLAYERS = 4;


  private final QuestionRepo questionRepo;
  private final GameDbService gameDbService;
  private final GameRoomManager gameRoomManager;
  private final GameNotificationService notificationService;

  private final String imageUrlPart;
  private final Random random = new Random();
  private final TaskScheduler taskScheduler;


  public GameService(GameNotificationService notificationService,
                     QuestionRepo questionRepo,
                     TaskScheduler taskScheduler,
                     @Value("${CLOUD_FRONT_URL}") String imageUrlPart,
                     GameDbService gameDbService,
                     GameRoomManager gameRoomManager) {
    this.notificationService = notificationService;
    this.questionRepo = questionRepo;
    this.taskScheduler = taskScheduler;
    this.imageUrlPart = imageUrlPart;
    this.gameDbService = gameDbService;
    this.gameRoomManager = gameRoomManager;
  }

  private void startGameAndPropagateQuestions(GameRoom room) {

    synchronized (room) {
      if (room.getStatus() != GameStatus.ROUND_FINISHED && room.getStatus() != GameStatus.COUNTDOWN) return;

      // find random question Ids
      if (room.getQIds() == null || room.getQIds().isEmpty()) {
        List<Long> randomQIds = questionRepo.getRandomQuestionIds(room.getQQuantity(), room.getTopic());
        if (randomQIds.isEmpty())
          throw new RuntimeException("No questions in DB");
        room.setQIds(new ArrayList<>(randomQIds)); // FIXME: надо ли это поле вообще в GameRoom
      }

      int currentQNum = (room.getCurrentQNum() == null || room.getCurrentQNum() == 0) ? 1
          : room.getCurrentQNum() + 1;

      // just in case
      if (currentQNum > room.getQIds().size()) {
        finishGame(room);
        return;
      }

      // find next question and its answers // FIXME: это текущий вопрос или следующий
      Long nextQId = room.getQIds().get(currentQNum - 1);
      Question q = questionRepo.findById(nextQId)
          .orElseThrow(() -> new RuntimeException("Question not found with id: " + nextQId));
      // set answers
      List<AnswerDto> answers = new ArrayList<>();
      for (Answer ans : q.getAnswers()) {
        answers.add(new AnswerDto(ans.getId(), ans.getText()));
      }

      Date current = Date.from(ZonedDateTime.now().toInstant());
      Date roundEnd = Date.from(ZonedDateTime.now().plusSeconds(ROUND_TIME).toInstant());

      for (Player p : room.getPlayers()) {
        p.setIsAnswered(false);
        p.setIsCurrentAnsCorrect(false);
      }

      room.setCurrentTime(current);
      room.setRoundEndTime(roundEnd);

      room.setStatus(GameStatus.ACTIVE);
      room.setCurrentQId(q.getId());
      room.setTopic(q.getTopic());
      room.setCurrentImageUrl(imageUrlPart + q.getImagePath());
      room.setCurrentQText(q.getText());
      room.setCurrentAnswers(answers);
      room.setCurrentQNum(currentQNum);

      notificationService.sendUpdatedRoom(room);

      // update CurrentTime as null after sending room
      room.setCurrentTime(null);

      // get Round Results once ROUND_TIME is finished
      ScheduledFuture<?> roundTask = taskScheduler.schedule(() -> getRoundResults(room), roundEnd);
      gameRoomManager.addTimer(room.getId(), roundTask);
    }
  }

  // happens when round's countdown is finished or all players answered in advance
  public void getRoundResults(GameRoom room) {

    // just in case: if it's not active then it's pointless to get results
    if (room.getStatus() != GameStatus.ACTIVE) return;

    // remove Round Time if users answered ahead of schedule
    gameRoomManager.removeTimer(room.getId());

    // if current question isn't the last one then set timer to send new question
    if (room.getCurrentQNum() < room.getQQuantity()) {
      Instant nextQ = ZonedDateTime.now().plusSeconds(ROUND_RESULTS_TIME).toInstant();

      room.setStatus(GameStatus.ROUND_FINISHED);
      room.setRoundEndTime(Date.from(nextQ));
      room.setCurrentTime(Date.from(ZonedDateTime.now().toInstant()));
      notificationService.sendUpdatedRoom(room);

      // update CurrentTime as null after sending room
      room.setCurrentTime(null);

      // update isAnswered for every player
      for (Player p : room.getPlayers()) {
        p.setIsAnswered(false);
      }

      // set timer for ROUND_RESULTS_TIME to start next round (i.e. propagate questions)
      ScheduledFuture<?> nextRoundTask = taskScheduler.schedule(() -> startGameAndPropagateQuestions(room), nextQ);
      gameRoomManager.addTimer(room.getId(), nextRoundTask);
    }
    // the end of the last round -> the end of the game
    else {
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

      for (Player p : room.getPlayers()) {
        boolean isWinner = winner != null && p.getId().equals(winner.getId());
        p.setIsWinner(isWinner);
      }

      gameDbService.saveFinalResultsToDb(room);
      finishGame(room);
    }
  }

  public void answerQ(Long qId, Long userId, Long answerId, String roomId) {
    GameRoom room = gameRoomManager.getRoom(roomId);
    if (room == null) return;

    boolean shouldFinishRound = false;

    synchronized (room) {
      Player currentPlayer = room.getPlayers().stream()
          .filter(p -> p.getId().equals(userId))
          .findFirst()
          .orElseThrow(() -> new RuntimeException("Player with id: " + userId + " not found in room"));

      if (currentPlayer.getIsAnswered()) return;

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
        gameRoomManager.removeTimer(roomId);
        shouldFinishRound = true;

      } else {
        notificationService.sendUpdatedRoom(room);
      }
    }
    if (shouldFinishRound) { getRoundResults(room); }
  }

  // --------------------------------------------------------------------------------------------------------------------



  private void finishGame(GameRoom room) {
    String roomId = room.getId();

    gameRoomManager.removeTimer(roomId);
    room.setStatus(GameStatus.FINISHED);
    notificationService.sendUpdatedRoom(room);

    Instant deleteTime = ZonedDateTime.now().plusSeconds(3).toInstant(); // todo: recheck, make a log that room was deleted successfully
    taskScheduler.schedule(() ->
        gameRoomManager.removeRoom(roomId), Date.from(deleteTime));
  }

  // DELETE user from game room
  public synchronized void leaveGameRoom(Long userId, String roomId) {

    GameRoom room = gameRoomManager.getRoom(roomId);
    if (room == null) {return;}

    // FIXME: надо ли это уловие вообще, если комната закончена и игрок уходит, тогда я выгоняю
    //  его из комнаты, какой смысл если комната и так удаляется когда игра заканчивается
    //  пользователь даже не сможет по логике выйти из игры если она закончена
    if (room.getStatus() == GameStatus.FINISHED) {
      room.getPlayers().removeIf(player -> player.getId().equals(userId));
      return;
    }
    // delete player from room
    room.getPlayers().removeIf(player -> player.getId().equals(userId));

    // TECHNICAL WIN if one player is remained in active room
    if (room.getPlayers().size() == 1 && (room.getStatus() == GameStatus.ACTIVE || room.getStatus() == GameStatus.ROUND_FINISHED)) {
      Player winner = room.getPlayers().getFirst();
      winner.setIsWinner(true);

      gameDbService.technicalWin(winner, room);
      finishGame(room);
      return;
    }

    // delete room if it's empty
    if (room.getPlayers().isEmpty()) {
      // remove timers if it has status COUNTDOWN // TODO: по идее я должен в любом случае удалять таймеры на всякий случай
      if (room.getStatus() == GameStatus.COUNTDOWN) {
        gameRoomManager.removeTimer(roomId);
      }
      gameRoomManager.removeRoom(roomId);
      return;
    }
    // change status of room if there's 1 player and
    // status of the room is COUNTDOWN // TODO: возможно порядок условий надо поменять
    if (room.getPlayers().size() < 2 &&
        room.getStatus() == GameStatus.COUNTDOWN) {
      gameRoomManager.removeTimer(roomId);
      room.setCountdownEndTime(null);
      room.setStatus(GameStatus.WAITING);
    }
    notificationService.sendUpdatedRoom(room);
  }

  // --------------------------------------------------------------------------------------------------------------------

  private void deleteRoomIfAllDisconnected(String roomId) {
    GameRoom room = gameRoomManager.getRoom(roomId);
    if (room == null) { return; } // if already deleted

    boolean hasConnectedPlayer = room.getPlayers().stream()
        .anyMatch(player -> player.getStatus() == PlayerStatus.CONNECTED);

    // if there's one connected user then game continues otherwise
    // all users are disconnected then room and timer are deleted
    if (!hasConnectedPlayer) {
      gameRoomManager.removeRoom(roomId);
      gameRoomManager.removeTimer(roomId); // todo это наверное лишнее если я удаляю таймер в мэнеджере
      log.info("GameRoom {} deleted due to inactivity", roomId);
    }
    // TODO: тут мб return надо сделать на случай если законекченные есть но мб это по дефолту и так
  }

  // case of disconnection (i.e. closed a tab or browser)
  public synchronized void disconnectGameRoom(Long userId) {
    Optional<GameRoom> roomOpt = gameRoomManager.findGameRoomByPlayerId(userId);

    if (roomOpt.isPresent()) {
      GameRoom room = roomOpt.get();

      // delete player form room if it's finished // TODO: не уверен что это надо потому что комната и так удалиться по идее я могу это вообще удалить потому что это ниже по сути прописано тоже самое
      if (room.getStatus() == GameStatus.FINISHED) {
        room.getPlayers().removeIf(p -> p.getId().equals(userId));
        if (room.getPlayers().isEmpty()) {
          gameRoomManager.removeRoom(room.getId());
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

      // change player status if user's disconnected from active room
      if (room.getStatus() == GameStatus.ACTIVE || room.getStatus() == GameStatus.ROUND_FINISHED) {
        targetPlayer.setStatus(PlayerStatus.DISCONNECTED);
      } else { // if room status COUNTDOWN or WAITING then player is removed
        room.getPlayers().remove(targetPlayer);
        // if room is empty then delete it // todo: удалить выше дублируется логика if FINISHED
        if (room.getPlayers().isEmpty()) {
          String roomId = room.getId();
          gameRoomManager.removeTimer(room.getId());  // todo это наверное лишнее если я удаляю таймер в мэнеджере
          gameRoomManager.removeRoom(roomId);
        }
      }

      long connectedPlayersCount = room.getPlayers().stream()
          .filter(player -> player.getStatus() == PlayerStatus.CONNECTED)
          .count();

      // if there's only one player and RoomStatus is COUNTDOWN then status changes to WAITING and remove timer
      String roomId = room.getId();
      if (connectedPlayersCount < 2 && room.getStatus() == GameStatus.COUNTDOWN) {
        gameRoomManager.removeTimer(roomId);
        room.setCountdownEndTime(null);
        room.setStatus(GameStatus.WAITING);
      }

      // if everyone in GameRoom has status DISCONNECTED -> room is deleted in 30 seconds if no one is connected
      if (connectedPlayersCount == 0 && room.getStatus() == GameStatus.ACTIVE) {
        Instant deleteTime = ZonedDateTime.now().plusSeconds(30).toInstant();
        try {
          taskScheduler.schedule(() -> deleteRoomIfAllDisconnected(roomId), deleteTime);
        } catch (TaskRejectedException e) {
          log.debug("Server is shutting down, scheduled deletion of the room {} cancelled", roomId);
        }
      }
      notificationService.sendUpdatedRoom(room);
    }
    // TODO: тут мб return надо сделать на случай если законекченные есть но мб это по дефолту и так в воид методе
  }

  // --------------------------------------------------------------------------------------------------------------------

  public List<String> findAllTopics() {
    List<String> topics = questionRepo.findAllTopics();
    if (topics.isEmpty()) {
      throw new RuntimeException("No Topics in Db");
    }
    return topics;
  }

  private void validateTopic(String qTopic) {
    if (!questionRepo.existsByTopic(qTopic)) {
      throw new FrontendException("Topic " + qTopic  + " not found");
    }
  }

  // метод для получения статуса игрока, чтобы понять переводить его в меню или в игру закидывать // todo: можно в отдельный класс закинуть
  public String getPlayerState(Long userId) {
    Optional<GameRoom> gameRoomOpt = gameRoomManager.findGameRoomByPlayerId(userId);
    if (gameRoomOpt.isEmpty()) {
      return PlayerStatus.NOT_IN_GAME.toString();
    }

    if (gameRoomOpt.get().getStatus() == GameStatus.FINISHED) {
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

  public synchronized GameRoomResponse joinGameRoom(String username, Long userId, String qTopic, int qQuantity) {
    // checking whether any room contains user or not,
    // if yes then reconnect happens i.e. return the user in that room and change its status on CONNECTED.
    // if user does not belong any room, then user is placed in available room with

    if (!"random".equals(qTopic))
      validateTopic(qTopic);
    GameRoom targetRoom;
    // RECONNECT
    Optional<GameRoom> existingGameRoomOpt = gameRoomManager.findGameRoomByPlayerId(userId);
    if (existingGameRoomOpt.isPresent()) {
      targetRoom = existingGameRoomOpt.get();
      for (Player p : targetRoom.getPlayers()) {
        if (p.getId().equals(userId)) {
          p.setStatus(PlayerStatus.CONNECTED);
          break;
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

        gameRoomManager.addTimer(targetRoom.getId(), timerTask);
      }

      String gameTopic = "/topic/game_room/" + targetRoom.getId();
      // 4 connected players -> countdown finished & start game
      if (connectedPlayersCount == MAX_PLAYERS) {
        gameRoomManager.removeTimer(targetRoom.getId());
        targetRoom.setCountdownEndTime(null);

        // start game & send the 1st question
        startGameAndPropagateQuestions(targetRoom);

        return new GameRoomResponse(gameTopic, GameMapper.toDto(targetRoom));
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
      startGameAndPropagateQuestions(room);
    }
  }

  // creating a brand new GameRoom
  private synchronized GameRoomResponse createRoom(String username, Long userId, Integer qQuantity, String qTopic) {
    if (qTopic.equals("random")) {
      List<String> topics = findAllTopics();
      qTopic = topics.get(random.nextInt(topics.size()));
    } else {
      validateTopic(qTopic);
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
}