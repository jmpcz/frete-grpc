package br.ufg.sd.gateway;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey chave;
    private final long expiracaoMinutos;

    public JwtService(@Value("${gateway.jwt.segredo}") String segredo,
                      @Value("${gateway.jwt.expiracao-min:60}") long expiracaoMinutos) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMinutos = expiracaoMinutos;
    }

    public String gerar(String usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoMinutos, ChronoUnit.MINUTES)))
                .signWith(chave)
                .compact();
    }

    public String validarEObterUsuario(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
