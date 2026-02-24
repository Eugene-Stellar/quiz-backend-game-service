package eugenestellar.repository;

import eugenestellar.model.entity.GamePlayer;
import eugenestellar.model.entity.GamePlayerComposedId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GamePlayerRepo extends JpaRepository<GamePlayer, GamePlayerComposedId> {
}
