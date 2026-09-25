package br.ufg.sd.gateway;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacao(MethodArgumentNotValidException e) {
        Map<String, String> erros = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> erros.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of(
                "mensagem", "Dados do pedido invalidos",
                "erros", erros));
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, Object>> grpc(StatusRuntimeException e) {
        Status.Code codigo = e.getStatus().getCode();
        HttpStatus http = switch (codigo) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAVAILABLE, DEADLINE_EXCEEDED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        String descricao = e.getStatus().getDescription();
        return ResponseEntity.status(http).body(Map.of(
                "mensagem", descricao != null ? descricao : "Falha ao processar a requisicao"));
    }
}
