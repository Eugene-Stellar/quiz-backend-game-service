//package eugenestellar.backendgame.model.dto;
//
//import eugenestellar.backendgame.model.PlayerStatus;
//import eugenestellar.backendgame.model.gameEntity.Player;
//import lombok.AllArgsConstructor;
//import lombok.Getter;
//import lombok.NoArgsConstructor;
//import lombok.Setter;
//
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//
//@Setter @Getter @AllArgsConstructor @NoArgsConstructor
//public class RoomQuestionDto {
//  // TODO: вынести все в отдельный класс чтобы
//  //  при реконекте фронту возвращался какой-то класс
//  //  где вся информация об играках и о вопросе если игра Active
//  private String qText;
//  private Long qId;
//  private int qNumber;
//  private int qQuantity;
//  private Date countDown;
//  private List<String> answers = new ArrayList<>(); // text of an answer
//  private List<Player> players = new ArrayList<>();
//}
//
//// TODO: dto которое нужно возвращать фронту при реконекте
////  players [{username, id, playerStatus, score, isAnswered}]
////  qText, qId, qNumber, qQuantity, countDown, answers