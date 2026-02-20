package eugenestellar.backendgame.repository;

import eugenestellar.backendgame.model.entity.GamePlayer;
import eugenestellar.backendgame.model.entity.GamePlayerComposedId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GamePlayerRepo extends JpaRepository<GamePlayer, GamePlayerComposedId> {
}
