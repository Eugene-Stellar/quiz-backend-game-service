package eugenestellar.exception.ws;

import jakarta.validation.ConstraintViolationException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Map;

@ControllerAdvice
public class WsExceptionHandler {

  @MessageExceptionHandler(ConstraintViolationException.class)
  @SendToUser("/queue/errors")
  public Map<String, String> handleValidationException(ConstraintViolationException ex) {

    String msg = ex.getConstraintViolations().iterator().next().getMessage();

    return Map.of("error", msg);
  }

  @MessageExceptionHandler(FrontendException.class)
  @SendToUser("/queue/errors")
  public Map<String, String> handleFrontendException(FrontendException ex) {
    return Map.of("error", ex.getMessage());
  }

//  @MessageExceptionHandler(RuntimeException.class)
//  @SendToUser("/queue/errors")
//  public Map<String, String> handleRuntimeException(RuntimeException ex) {
//    return Map.of("error", ex.getMessage());
//  }

}