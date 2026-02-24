package eugenestellar.repository;

import eugenestellar.model.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepo extends JpaRepository<Question, Long> {
  @Query(value = "SELECT id FROM questions WHERE topic = :topic ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
  List<Long> getRandomQuestionIds(@Param("limit") int limit, @Param("topic") String topic);

  Boolean existsByTopic(String topic);

  @Query("SELECT DISTINCT q.topic FROM Question q")
  List<String> findAllTopics();

}