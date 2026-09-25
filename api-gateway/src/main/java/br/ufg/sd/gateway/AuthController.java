package br.ufg.sd.gateway;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final String usuario;
    private final String senha;

    public AuthController(JwtService jwtService,
                          @Value("${gateway.auth.usuario}") String usuario,
                          @Value("${gateway.auth.senha}") String senha) {
        this.jwtService = jwtService;
        this.usuario = usuario;
        this.senha = senha;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        if (usuario.equals(req.usuario()) && senha.equals(req.senha())) {
            return ResponseEntity.ok(new TokenResponse(jwtService.gerar(req.usuario())));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("mensagem", "Credenciais invalidas"));
    }
}
