package eugenestellar.model.dto.mapper;

import eugenestellar.model.dto.GameRoomDto;
import eugenestellar.model.gameEntity.GameRoom;
import eugenestellar.model.gameEntity.Player;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GameMapper {

  public static GameRoomDto toDto(GameRoom room) {
    GameRoomDto.GameRoomDtoBuilder builder = GameRoomDto.builder()
        .id(room.getId())
        .status(room.getStatus())
        .gameId(room.getGameId())
        .currentImageUrl(room.getCurrentImageUrl())
        .currentAnswers(room.getCurrentAnswers())
        .qQuantity(room.getQQuantity())
        .currentQText(room.getCurrentQText())
        .currentQId(room.getCurrentQId())
        .countdownEndTime(room.getCountdownEndTime())
        .currentQNum(room.getCurrentQNum())
        .roundEndTime(room.getRoundEndTime())
        .currentTime(room.getCurrentTime())
        .topic(room.getTopic());

    List<Player> players = maskPlayers(room);
    builder.players(players);

    maskRoom(builder, room);
    return builder.build();
  }

  private static List<Player> maskPlayers(GameRoom room) {
    ArrayList<Player> playersDto = new ArrayList<>();
    List<Player> playersSnapshot = new ArrayList<>(room.getPlayers()); // protection from in-time changed Player List

    for (Player p : playersSnapshot) {
      Player.PlayerBuilder pb = p.toBuilder();

      switch (room.getStatus()) {
        case WAITING, COUNTDOWN ->
          pb
            .isAnswered(null)
            .isCurrentAnsCorrect(null)
            .isWinner(null)
            .score(null);
        case ACTIVE -> {
          int realScore = (p.getScore() == null) ? 0 : p.getScore();
          boolean isCorrect = Boolean.TRUE.equals(p.getIsCurrentAnsCorrect());
          pb
            .isCurrentAnsCorrect(null)
            .isWinner(null)
            .score(isCorrect ? realScore - 10 : realScore); // does not show real score during active state
        }
        case ROUND_FINISHED ->
          pb
            .isAnswered(null)
            .isWinner(null);
        case FINISHED ->
            pb
              .isAnswered(null)
              .status(null)
              .isCurrentAnsCorrect(null);
      }
      playersDto.add(pb.build());
    }
    return playersDto;
  }

  private static void maskRoom(GameRoomDto.GameRoomDtoBuilder builder, GameRoom room) {
    Date timeStamp = (room.getCurrentTime() == null) ? Date.from(ZonedDateTime.now().toInstant())
        : room.getCurrentTime();
    switch (room.getStatus()) {
      case WAITING ->
        builder
            .currentTime(null)
            .gameId(null);
      case COUNTDOWN ->
        builder
            .currentTime(timeStamp)
            .currentQText(null)
            .currentQId(null)
            .currentAnswers(null)
            .currentQNum(null)
            .qQuantity(null)
            .gameId(null)
            .roundEndTime(null);
      case ACTIVE ->
        builder
            .currentTime(timeStamp)
            .countdownEndTime(null)
            .gameId(null);
      case ROUND_FINISHED ->
        builder
            .countdownEndTime(null)
            .currentAnswers(null)
            .currentQId(null)
            .currentQText(null)
            .qQuantity(null)
            .currentTime(timeStamp)
            .gameId(null)
            .roundEndTime(room.getRoundEndTime());
      case FINISHED ->
        builder
            .currentQText(null)
            .currentQId(null)
            .currentAnswers(null)
            .currentQNum(null)
            .qQuantity(null)
            .countdownEndTime(null)
            .roundEndTime(null);
    }
  }
}