package eugenestellar.service;

import eugenestellar.exception.ws.FrontendException;
import eugenestellar.repository.QuestionRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionService {

  private final QuestionRepo questionRepo;

  public QuestionService(QuestionRepo questionRepo) {
    this.questionRepo = questionRepo;
  }

  public List<String> findAllTopics() {
    List<String> topics = questionRepo.findAllTopics();
    if (topics.isEmpty()) {
      throw new RuntimeException("No Topics in Db");
    }
    return topics;
  }

  public void validateTopic(String qTopic) {
    if (!questionRepo.existsByTopic(qTopic)) {
      throw new FrontendException("Topic " + qTopic  + " not found");
    }
  }
}