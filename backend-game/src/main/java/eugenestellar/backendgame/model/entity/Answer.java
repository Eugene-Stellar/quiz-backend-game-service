package eugenestellar.backendgame.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Immutable
@Table(name = "answers")
public class Answer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "text", nullable = false)
  private String text;

  @Column(name = "is_correct", insertable = false, updatable = false, nullable = false)
  private Boolean isCorrect;

  @ManyToOne
  @JoinColumn(name = "question_id")
  private Question question;
}