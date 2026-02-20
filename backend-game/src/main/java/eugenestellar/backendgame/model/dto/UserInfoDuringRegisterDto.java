package eugenestellar.backendgame.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class UserInfoDuringRegisterDto {

  @NotBlank(message = "Name can't be empty")
  @Size(max = 20, min = 3, message = "Username length must be between 3 and 20 characters") // code level
  @Pattern(regexp = "^[\\p{L}\\p{N}_]+$", message = "Username can contain only letters, numbers and underscore")
  private String username;

}