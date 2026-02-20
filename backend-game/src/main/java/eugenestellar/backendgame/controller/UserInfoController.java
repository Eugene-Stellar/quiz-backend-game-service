package eugenestellar.backendgame.controller;

import eugenestellar.backendgame.model.dto.UserInfoDuringRegisterDto;
import eugenestellar.backendgame.service.UserInfoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user_info")
public class UserInfoController {

  private final UserInfoService userInfoService;

  public UserInfoController(UserInfoService userInfoService) {
    this.userInfoService = userInfoService;
  }

  @PostMapping("/{id}")
  public ResponseEntity<?> setUserInfo(@PathVariable Long id,
                                       @Valid @RequestBody UserInfoDuringRegisterDto userInfoDtoDuringRegister) {
    userInfoService.saveUserInfoDuringRegister(id, userInfoDtoDuringRegister);
    return ResponseEntity.ok().build();
  }
}