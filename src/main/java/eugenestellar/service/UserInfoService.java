package eugenestellar.service;

import eugenestellar.exception.rest.UserInfoCreationException;
import eugenestellar.model.dto.UserInfoDuringRegisterDto;
import eugenestellar.model.entity.UserInfo;
import eugenestellar.repository.UserInfoRepo;
import org.springframework.stereotype.Service;

@Service
public class UserInfoService {

  private final UserInfoRepo userInfoRepo;

  public UserInfoService(UserInfoRepo userInfoRepo) {
    this.userInfoRepo = userInfoRepo;
  }

  public void saveUserInfoDuringRegister(long id, UserInfoDuringRegisterDto userInfoDtoDuringRegister) {
    UserInfo userInfo = new UserInfo();
    userInfo.setId(id);
    userInfo.setUsername(userInfoDtoDuringRegister.getUsername());
    userInfo.setWins(0);
    userInfo.setTotalScore(0);
    userInfo.setLosses(0);
    userInfo.setGameQuantity(0);
    try {
      userInfoRepo.save(userInfo);
    } catch (Exception e) {
      throw new UserInfoCreationException("Failed to save user info", e);
    }
  }
}