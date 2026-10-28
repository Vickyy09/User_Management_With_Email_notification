package com.vicky.userManagment;

import com.vicky.userManagment.dto.CreateUserRequest;
import com.vicky.userManagment.entity.*;
import com.vicky.userManagment.repo.*;
import com.vicky.userManagment.security.TokenService;
import com.vicky.userManagment.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagmentApplicationTests {
    private static final String PASSWORD = "Correct horse 123!";
    private static final String NEW_PASSWORD = "Another strong 456!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepo users;
    @Autowired AuthTokenRepo tokens;
    @Autowired EmailNotificationRepo notifications;
    @Autowired PasswordEncoder encoder;
    @Autowired EmailDispatcher dispatcher;
    @Autowired EmailDeliveryService delivery;
    @Autowired UserServiceImpl service;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender sender;

    @BeforeEach
    void cleanDatabase() {
        tokens.deleteAll();
        notifications.deleteAll();
        users.deleteAll();
        reset(sender);
    }

    @Test
    void registrationHashesPasswordAndQueuesNotificationsWithoutSending() throws Exception {
        long id = register("Person@Example.test");
        var user = users.findById(id).orElseThrow();
        assertThat(user.getEmail()).isEqualTo("person@example.test");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).startsWith("$2a$12$").isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(notifications.findAll()).hasSize(2).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(EmailNotification.Status.PENDING);
            assertThat(n.getBody()).doesNotContain(PASSWORD, user.getPasswordHash());
        });
        verifyNoInteractions(sender);
    }

    @Test
    void invalidFieldsReturnUsefulErrors() throws Exception {
        var body = createBody("not-an-email");
        body.put("firstName", " ");
        body.put("password", "short");
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        assertThat(users.count()).isZero();
        assertThat(notifications.count()).isZero();
    }

    @Test
    void bcryptByteLimitIsValidatedForUnicodePasswords() throws Exception {
        var body = createBody("unicode@example.test");
        body.put("password", "\u00e9".repeat(37));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void registrationRejectsClientSuppliedRole() throws Exception {
        var body = createBody("attacker@example.test");
        body.put("role", "ADMIN");
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json(body)))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();
    }

    @Test
    void duplicateEmailIsCaseInsensitiveAndDoesNotQueueAnotherNotification() throws Exception {
        register("duplicate@example.test");
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json(createBody("DUPLICATE@example.test"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Email is already registered"));
        assertThat(users.count()).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test
    void databaseAlsoEnforcesEmailUniqueness() throws Exception {
        register("unique@example.test");
        var duplicate = new Users();
        duplicate.setEmail("unique@example.test");
        assertThatThrownBy(() -> users.saveAndFlush(duplicate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void anonymousAndMalformedTokenRequestsReturnJson401() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401)).andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Basic invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownAccount() throws Exception {
        register("login@example.test");
        for (String email : List.of("login@example.test", "missing@example.test")) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                            .content(json(Map.of("email", email, "password", "Wrong password 123!"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid credentials or inactive account"));
        }
    }

    @Test
    void tokensAreHashedAndExpire() throws Exception {
        register("token@example.test");
        String raw = login("token@example.test", PASSWORD);
        assertThat(tokens.existsById(raw)).isFalse();
        var stored = tokens.findById(TokenService.hash(raw)).orElseThrow();
        assertThat(stored.getExpiresAt()).isAfter(Instant.now());
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        tokens.saveAndFlush(stored);
        mvc.perform(get("/api/users/me").header("Authorization", bearer(raw))).andExpect(status().isUnauthorized());
    }

    @Test
    void userCanReadOwnProfileButNotAnotherUsersProfileOrAdminOperations() throws Exception {
        long own = register("own@example.test");
        long other = register("other@example.test");
        String raw = login("own@example.test", PASSWORD);
        mvc.perform(get("/api/users/me").header("Authorization", bearer(raw)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(own))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/users/" + other).header("Authorization", bearer(raw)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/" + other).header("Authorization", bearer(raw))
                        .contentType("application/json").content(json(updateBody("other@example.test"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", bearer(raw))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/" + other).header("Authorization", bearer(raw))).andExpect(status().isForbidden());
        mvc.perform(post("/api/users").header("Authorization", bearer(raw))
                        .contentType("application/json").content(json(createBody("admin-only@example.test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileUpdateQueuesNotificationAndKeepsRole() throws Exception {
        long id = register("update@example.test");
        String raw = login("update@example.test", PASSWORD);
        mvc.perform(put("/api/users/me").header("Authorization", bearer(raw))
                        .contentType("application/json").content(json(updateBody("update@example.test"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.role").value("USER"));
        assertThat(users.findById(id).orElseThrow().getFirstName()).isEqualTo("Updated");
        assertThat(notifications.findAll().stream().filter(n -> n.getSubject().equals("Profile updated"))).hasSize(2);
    }

    @Test
    void profileUpdateRejectsProtectedFields() throws Exception {
        register("protected@example.test");
        String raw = login("protected@example.test", PASSWORD);
        for (String field : List.of("role", "id", "active", "passwordHash")) {
            var body = updateBody("protected@example.test");
            body.put(field, "ADMIN");
            mvc.perform(put("/api/users/me").header("Authorization", bearer(raw))
                            .contentType("application/json").content(json(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void changingEmailRevokesTokensAndDuplicateUpdateIsRejected() throws Exception {
        register("first@example.test");
        register("second@example.test");
        String raw = login("first@example.test", PASSWORD);
        mvc.perform(put("/api/users/me").header("Authorization", bearer(raw))
                        .contentType("application/json").content(json(updateBody("SECOND@example.test"))))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/users/me").header("Authorization", bearer(raw))
                        .contentType("application/json").content(json(updateBody("new@example.test"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(raw))).andExpect(status().isUnauthorized());
        login("new@example.test", PASSWORD);
    }

    @Test
    void adminCanCreateAndListUsersWithBoundedPagination() throws Exception {
        String admin = adminToken();
        mvc.perform(post("/api/users").header("Authorization", bearer(admin))
                        .contentType("application/json").content(json(createBody("created@example.test"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/users?page=0&size=1&sort=email&direction=desc").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
        for (String query : List.of("size=101", "page=-1", "sort=passwordHash", "direction=invalid")) {
            mvc.perform(get("/api/users?" + query).header("Authorization", bearer(admin)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void missingUsersReturn404AndInvalidIdsReturn400() throws Exception {
        String admin = adminToken();
        mvc.perform(get("/api/users/999999").header("Authorization", bearer(admin))).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/999999").header("Authorization", bearer(admin))
                        .contentType("application/json").content(json(updateBody("missing@example.test"))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/999999").header("Authorization", bearer(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/0").header("Authorization", bearer(admin))).andExpect(status().isBadRequest());
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndRevokesAllTokens() throws Exception {
        long id = register("password@example.test");
        String first = login("password@example.test", PASSWORD);
        String second = login("password@example.test", PASSWORD);
        mvc.perform(put("/api/users/me/password").header("Authorization", bearer(first))
                        .contentType("application/json")
                        .content(json(Map.of("currentPassword", "Wrong password 123!", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/users/me/password").header("Authorization", bearer(first))
                        .contentType("application/json")
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/users/me/password").header("Authorization", bearer(first))
                        .contentType("application/json")
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());
        for (String raw : List.of(first, second)) {
            mvc.perform(get("/api/users/me").header("Authorization", bearer(raw))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json(Map.of("email", "password@example.test", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        login("password@example.test", NEW_PASSWORD);
        assertThat(encoder.matches(NEW_PASSWORD, users.findById(id).orElseThrow().getPasswordHash())).isTrue();
        assertThat(notifications.findAll().stream().filter(n -> n.getSubject().equals("Password changed")))
                .singleElement().satisfies(n -> {
                    assertThat(n.getRecipient()).isEqualTo("password@example.test");
                    assertThat(n.getBody()).doesNotContain(PASSWORD, NEW_PASSWORD);
                });
    }

    @Test
    void deactivationPreservesRecordBlocksLoginAndIsIdempotent() throws Exception {
        String admin = adminToken();
        long id = register("deactivate@example.test");
        String raw = login("deactivate@example.test", PASSWORD);
        mvc.perform(delete("/api/users/" + id).header("Authorization", bearer(admin))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/users/" + id).header("Authorization", bearer(admin))).andExpect(status().isNoContent());
        assertThat(users.findById(id).orElseThrow().isActive()).isFalse();
        mvc.perform(get("/api/users/me").header("Authorization", bearer(raw))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json(Map.of("email", "deactivate@example.test", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        assertThat(notifications.findAll().stream().filter(n -> n.getSubject().equals("Account deactivated"))).hasSize(2);
    }

    @Test
    void logoutRevokesOnlyTheCurrentToken() throws Exception {
        register("logout@example.test");
        String first = login("logout@example.test", PASSWORD);
        String second = login("logout@example.test", PASSWORD);
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(first))).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(first))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(second))).andExpect(status().isOk());
    }

    @Test
    void legacyUsersWithoutPasswordsCannotLogIn() throws Exception {
        var user = new Users();
        user.setEmail("legacy@example.test");
        users.saveAndFlush(user);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json(Map.of("email", "legacy@example.test", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapCreatesAdminButNeverPromotesExistingRegularUser() throws Exception {
        service.bootstrapAdmin(new CreateUserRequest("Admin", "User", "bootstrap@example.test", PASSWORD, null, null));
        assertThat(users.findByEmailIgnoreCase("bootstrap@example.test").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        register("regular@example.test");
        assertThatThrownBy(() -> service.bootstrapAdmin(
                new CreateUserRequest("Admin", "User", "regular@example.test", PASSWORD, null, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.findByEmailIgnoreCase("regular@example.test").orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void notificationsAreSentOnAnAsyncWorkerAndNotResent() throws Exception {
        register("async@example.test");
        var thread = new AtomicReference<String>();
        doAnswer(invocation -> { thread.set(Thread.currentThread().getName()); return null; })
                .when(sender).send(any(SimpleMailMessage.class));
        dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        assertThat(thread.get()).startsWith("email-worker-");
        assertThat(notifications.findAll()).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(EmailNotification.Status.SENT);
            assertThat(n.getSentAt()).isNotNull();
        });
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpFailurePreservesUserAndStopsAfterThreeAttempts() throws Exception {
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        long id = register("failure@example.test");
        for (int attempt = 0; attempt < 3; attempt++) {
            makeNotificationsDue();
            dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        }
        dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        assertThat(users.existsById(id)).isTrue();
        assertThat(notifications.findAll()).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(EmailNotification.Status.FAILED);
            assertThat(n.getAttempts()).isEqualTo(3);
            assertThat(n.getLastError()).isEqualTo("MailSendException");
        });
        verify(sender, times(6)).send(any(SimpleMailMessage.class));
    }

    @Test
    void failedDeliveryWaitsUntilDueAndCanRecover() throws Exception {
        register("recover@example.test");
        doThrow(new MailSendException("temporary")).when(sender).send(any(SimpleMailMessage.class));
        dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        var pending = notifications.findAll();
        assertThat(pending).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(EmailNotification.Status.PENDING);
            n.setNextAttemptAt(Instant.now().plusSeconds(3600));
        });
        notifications.saveAllAndFlush(pending);
        dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
        reset(sender);
        makeNotificationsDue();
        dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        assertThat(notifications.findAll()).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(EmailNotification.Status.SENT);
            assertThat(n.getAttempts()).isEqualTo(2);
            assertThat(n.getLastError()).isNull();
        });
    }

    @Test
    void permanentMailAuthenticationFailureIsNotRetried() throws Exception {
        register("mail-auth@example.test");
        doThrow(new MailAuthenticationException("bad credentials")).when(sender).send(any(SimpleMailMessage.class));
        dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        makeNotificationsDue();
        dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        assertThat(notifications.findAll()).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(EmailNotification.Status.FAILED);
            assertThat(n.getAttempts()).isEqualTo(1);
        });
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void rollbackRemovesBothUserAndQueuedMessages() {
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.register(new CreateUserRequest("Test", "User", "rollback@example.test", PASSWORD, null, null));
            throw new IllegalStateException("Simulated transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(users.count()).isZero();
        assertThat(notifications.count()).isZero();
        verifyNoInteractions(sender);
    }

    @Test
    void workerCannotSendUncommittedNotifications() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            service.register(new CreateUserRequest("Test", "User", "commit@example.test", PASSWORD, null, null));
            try {
                dispatcher.dispatch().get(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            verifyNoInteractions(sender);
        });
        dispatcher.dispatch().get(10, TimeUnit.SECONDS);
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void concurrentWorkersDoNotSendTheSameCommittedNotificationTwice() throws Exception {
        register("concurrent@example.test");
        Long id = notifications.findAll().getFirst().getId();
        var first = CompletableFuture.runAsync(() -> delivery.deliver(id));
        var second = CompletableFuture.runAsync(() -> delivery.deliver(id));
        CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(notifications.findById(id).orElseThrow().getAttempts()).isEqualTo(1);
    }


    @Test
    void apiSpecificationIsAvailableWithoutAuthentication() throws Exception {
        mvc.perform(get("/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.3"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
    private long register(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType("application/json").content(json(createBody(email))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        long id = register("admin@example.test");
        var admin = users.findById(id).orElseThrow();
        admin.setRole(Role.ADMIN);
        users.saveAndFlush(admin);
        return login("admin@example.test", PASSWORD);
    }

    private Map<String, Object> createBody(String email) {
        var body = updateBody(email);
        body.put("firstName", "Test");
        body.put("password", PASSWORD);
        return body;
    }

    private Map<String, Object> updateBody(String email) {
        var body = new LinkedHashMap<String, Object>();
        body.put("firstName", "Updated");
        body.put("lastName", "User");
        body.put("email", email);
        body.put("phoneNumber", "+91 9876543210");
        body.put("department", "Engineering");
        return body;
    }

    private void makeNotificationsDue() {
        var all = notifications.findAll();
        all.forEach(n -> n.setNextAttemptAt(Instant.now().minusSeconds(1)));
        notifications.saveAllAndFlush(all);
    }

    private String json(Object value) { return mapper.writeValueAsString(value); }
    private String bearer(String token) { return "Bearer " + token; }
}