package eugenestellar.service;

import eugenestellar.model.GameStatus;
import eugenestellar.model.dto.AnswerDto;
import eugenestellar.model.entity.Answer;
import eugenestellar.model.entity.Question;
import eugenestellar.model.gameEntity.GameRoom;
import eugenestellar.model.gameEntity.Player;
import eugenestellar.repository.QuestionRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;

@Service
public class GameRoundService {

  // TIME
  private static final int ROUND_TIME = 20;
  private static final int ROUND_RESULTS_TIME = 5;
  private final String imageUrlPart;

  private final QuestionRepo questionRepo;
  private final GameRoomManagerService gameRoomManager;
  private final GameNotificationService notificationService;
  private final GameDbService gameDbService;
  private final TaskScheduler taskScheduler;
  private final Random random = new Random();

  public GameRoundService(GameDbService gameDbService,
                          @Value("${CLOUD_FRONT_URL}") String imageUrlPart,
                          QuestionRepo questionRepo,
                          GameRoomManagerService gameRoomManager,
                          GameNotificationService notificationService,
                          TaskScheduler taskScheduler) {
    this.gameDbService = gameDbService;
    this.imageUrlPart = imageUrlPart;
    this.questionRepo = questionRepo;
    this.gameRoomManager = gameRoomManager;
    this.notificationService = notificationService;
    this.taskScheduler = taskScheduler;
  }

  public void finishGame(GameRoom room) {
    String roomId = room.getId();

    gameRoomManager.removeTimer(roomId);
    room.setStatus(GameStatus.FINISHED);
    notificationService.sendUpdatedRoom(room);

    Instant deleteTime = ZonedDateTime.now().plusSeconds(3).toInstant();
    taskScheduler.schedule(() ->
        gameRoomManager.removeRoom(roomId), deleteTime);
  }

  public void startGameAndPropagateQuestions(GameRoom room) {

    synchronized (room) {
      if (room.getStatus() != GameStatus.ROUND_FINISHED && room.getStatus() != GameStatus.COUNTDOWN) return;

      // find random question Ids
      if (room.getQIds() == null || room.getQIds().isEmpty()) {
        List<Long> randomQIds = questionRepo.getRandomQuestionIds(room.getQQuantity(), room.getTopic());
        if (randomQIds.isEmpty())
          throw new RuntimeException("No questions in DB");
        room.setQIds(new ArrayList<>(randomQIds)); // ensure random set of questions during the game
      }

      int currentQNum = (room.getCurrentQNum() == null || room.getCurrentQNum() == 0) ? 1
          : room.getCurrentQNum() + 1;

      // just in case
      if (currentQNum > room.getQIds().size()) {
        finishGame(room);
        return;
      }

      // find next (current) question and its answers
      Long currentQId = room.getQIds().get(currentQNum - 1);
      Question q = questionRepo.findById(currentQId)
          .orElseThrow(() -> new RuntimeException("Question not found with id: " + currentQId));
      // set answers
      List<AnswerDto> answers = new ArrayList<>();
      for (Answer ans : q.getAnswers()) {
        answers.add(new AnswerDto(ans.getId(), ans.getText()));
      }

      Date current = Date.from(ZonedDateTime.now().toInstant());

      Instant roundEnd = ZonedDateTime.now().plusSeconds(ROUND_TIME).toInstant();


      for (Player p : room.getPlayers()) {
        p.setIsAnswered(false);
        p.setIsCurrentAnsCorrect(false);
      }

      room.setCurrentTime(current);
      room.setRoundEndTime(Date.from(roundEnd));

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

    synchronized (room) {
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
}