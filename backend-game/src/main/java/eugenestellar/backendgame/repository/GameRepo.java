package eugenestellar.backendgame.repository;

import eugenestellar.backendgame.model.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepo extends JpaRepository<Game, Long> {
}