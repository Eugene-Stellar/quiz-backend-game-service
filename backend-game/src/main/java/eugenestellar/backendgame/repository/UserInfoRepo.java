package eugenestellar.backendgame.repository;

import eugenestellar.backendgame.model.entity.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInfoRepo extends JpaRepository<UserInfo, Long> {
}
