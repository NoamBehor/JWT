package com.example.jwtlesson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mirrors the 19 automated checks in {@code http/security-tests.http}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityHttpFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String adminToken;
    private static String userToken;
    private static String forgedToken;
    private static String newUsername;

    @Test
    @Order(1)
    void publicEndpointIsOpen() throws Exception {
        mockMvc.perform(get("/api/public/hello")).andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void wrongPasswordReturns401() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("Invalid username or password");
    }

    @Test
    @Order(4)
    void unknownUserReturnsSame401Message() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no_such_user\",\"password\":\"whatever123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("Invalid username or password");
    }

    @Test
    @Order(5)
    void emptyLoginFieldsReturn400() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("fields").get("username")).isNotNull();
    }

    @Test
    @Order(6)
    void adminLoginReturnsJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("token").asText().split("\\.")).hasSize(3);
        assertThat(body.get("roles").toString()).contains("ROLE_ADMIN");
        adminToken = body.get("token").asText();
    }

    @Test
    @Order(7)
    void userLoginReturnsRoleUserOnlyAndBuildForgedToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user\",\"password\":\"User123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("roles")).hasSize(1);
        assertThat(body.get("roles").get(0).asText()).isEqualTo("ROLE_USER");
        userToken = body.get("token").asText();

        String[] userParts = userToken.split("\\.");
        String[] adminParts = adminToken.split("\\.");
        forgedToken = userParts[0] + "." + adminParts[1] + "." + userParts[2];
    }

    @Test
    @Order(8)
    void userTokenOnUserEndpointReturns200() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("username").asText()).isEqualTo("user");
        assertThat(body.get("password")).isNull();
    }

    @Test
    @Order(9)
    void userTokenOnAdminEndpointReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void adminListsUsersCleanJson() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode users = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(users.isArray()).isTrue();

        JsonNode admin = null;
        for (JsonNode u : users) {
            assertThat(u.get("password")).isNull();
            assertThat(u.get("roles").get(0).isTextual()).isTrue();
            if ("admin".equals(u.get("username").asText())) {
                admin = u;
            }
        }
        assertThat(admin).isNotNull();
        assertThat(admin.get("roles")).hasSize(2);
    }

    @Test
    @Order(11)
    void adminTokenOnUserEndpointReturns200() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("username").asText()).isEqualTo("admin");
    }

    @Test
    @Order(12)
    void forgedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(13)
    void garbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer this.is.not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(14)
    void missingBearerPrefixReturns401() throws Exception {
        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", userToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(15)
    void registerReturns201WithRoleUserOnly() throws Exception {
        newUsername = "student_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", newUsername,
                                "password", "Student123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("roles")).hasSize(1);
        assertThat(body.get("roles").get(0).asText()).isEqualTo("ROLE_USER");
        assertThat(body.get("password")).isNull();
    }

    @Test
    @Order(16)
    void newUserCanLogIn() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", newUsername,
                                "password", "Student123!"))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(17)
    void existingUsernameReturns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"TryToTakeOver1!\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(18)
    void shortPasswordReturns400() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "shorty_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6),
                                "password", "123"))))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("fields").get("password")).isNotNull();
    }

    @Test
    @Order(19)
    void clientCannotChooseAdminRole() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", "hacker_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6));
        payload.put("password", "Hacker123!");
        payload.put("roles", new String[]{"ROLE_ADMIN"});

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();

        int status = result.getResponse().getStatus();
        boolean safe = status == 400
                || (status == 201
                && !objectMapper.readTree(result.getResponse().getContentAsString()).get("roles").toString()
                .contains("ROLE_ADMIN"));
        assertThat(safe).isTrue();
    }
}
