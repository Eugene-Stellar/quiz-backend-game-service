package eugenestellar.repository;

import eugenestellar.model.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepo extends JpaRepository<Game, Long> {
}