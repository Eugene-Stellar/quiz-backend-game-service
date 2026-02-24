package eugenestellar.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.util.ArrayList;
import java.util.List;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Immutable
@Table(name = "questions")
public class Question {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "text", nullable = false)
  private String text;

  @Column(name = "image_path", nullable = false)
  private String imagePath;

  @Column(name = "topic", nullable = false)
  private String topic;

  @OneToMany(mappedBy = "question", fetch = FetchType.EAGER)
  private List<Answer> answers = new ArrayList<>();
}