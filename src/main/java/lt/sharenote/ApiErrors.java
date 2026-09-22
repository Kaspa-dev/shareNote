package lt.sharenote;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ProblemDetail apiError(ApiException ex) {
        return ProblemDetail.forStatusAndDetail(ex.status, ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Payload laukų reikšmės neatitinka reikalavimų.");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage())).toList());
        return handleExceptionInternal(ex, problem, headers, HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrityError(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Operacija pažeistų duomenų ryšius. Pirmiausia pašalinkite priklausomus resursus.");
    }

    record FieldError(String field, String message) {}
}
