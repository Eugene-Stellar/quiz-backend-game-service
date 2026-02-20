package eugenestellar.backendgame.repository;

import eugenestellar.backendgame.model.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionRepo extends JpaRepository<Question, Long> {
  @Query(value = "SELECT id FROM questions ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
  List<Long> getRandomQuestionIds(@Param("limit") int limit);

  Question getQuestionsById(Long id);

}