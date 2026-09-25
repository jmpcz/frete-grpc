package br.ufg.sd.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void loginComCredenciaisValidasDevolveToken() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginComCredenciaisInvalidasDevolve401() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pedidoSemTokenDevolve401() throws Exception {
        mvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pedidoComTokenECorpoInvalidoDevolve400() throws Exception {
        String token = jwtService.gerar("admin");
        mvc.perform(post("/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"\",\"quantidade\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados do pedido invalidos"))
                .andExpect(jsonPath("$.erros.item").exists());
    }
}
