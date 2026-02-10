package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.testcontainers.utility.DockerImageName.parse;

@AutoConfigureMockMvc
@SpringBootTest(classes = NatsTestApplication.class)
class NatsIdempotentControllerTest {
    private static final int MAX_RETRIES = 5;
    private static final NatsContainer NATS_CONTAINER = new NatsContainer(parse("nats:latest"));
    private static final String UPDATE_ID = UUID.randomUUID().toString();
    private static final String CHANGE_TITLE_ID = UUID.randomUUID().toString();
    private static final String GENERATED_KEY = UUID.randomUUID().toString();

    @Autowired
    private MockMvcTester mvcTester;

    @Autowired
    private Connection connection;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("idempotent.nats.servers", NATS_CONTAINER::getServerUrl);
    }

    @BeforeAll
    static void setUp() {
        NATS_CONTAINER.start();
    }

    @AfterAll
    static void tearDown() {
        NATS_CONTAINER.stop();
    }

    static void clearStoreIfLast(RepetitionInfo repetitionInfo) {
        if (repetitionInfo.getCurrentRepetition() == repetitionInfo.getTotalRepetitions()) {
            getStore().clear();
        }
    }

    private static void assertRepetition(RepetitionInfo repetitionInfo) {
        assertThat(repetitionInfo.getFailureCount()).isZero();
        List<String> isbns = getStore().values().stream()
                .map(NatsTestApplication.NatsIdempotentController.Book::isbn)
                .toList();
        assertThat(isbns.size()).isEqualTo(isbns.stream().distinct().count());
    }

    private static Map<String, NatsTestApplication.NatsIdempotentController.Book> getStore() {
        return NatsTestApplication.NatsIdempotentController.STORE;
    }

    private KeyValue keyValue() {
        try {
            return connection.keyValue("idempotent");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @RepeatedTest(MAX_RETRIES)
    @Execution(ExecutionMode.CONCURRENT)
    void shouldCreateBook(RepetitionInfo repetitionInfo) throws JetStreamApiException, IOException {
        mvcTester
                .post()
                .uri("/nats/books")
                .contentType(MediaType.APPLICATION_JSON)
                // language=json
                .content("""
            {
              "isbn": "978-0-385-33312-0",
              "category": {
                "genre": "Comedy",
                "subGenre": "Absurdist"
              },
              "title": "The Hitchhiker's Guide to the Galaxy",
              "author": "Douglas Adams"
            }
            """)
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .hasNoNullFieldsOrProperties()
                .hasPath("isbn")
                .hasPath("title")
                .hasPath("author")
                .hasPath("createdAt")
                .hasPath("category")
                .hasPath("category.genre")
                .hasPath("category.subGenre");

        var ik = new IdempotentStore.IdempotentKey("978-0-385-33312-0", "__NatsIdempotentController.create()");
        assertThat(keyValue().get(NatsIdempotentStore.encodeIfNotValid(ik)))
                .satisfies(kv -> assertThat(kv).isNotNull());
        assertRepetition(repetitionInfo);
        clearStoreIfLast(repetitionInfo);
    }

    @RepeatedTest(MAX_RETRIES)
    @Execution(ExecutionMode.CONCURRENT)
    void shouldManageInvalidKey(RepetitionInfo repetitionInfo) throws JetStreamApiException, IOException {
        String isbn = " .a!sd-.123";
        mvcTester
                .post()
                .uri("/nats/books")
                .contentType(MediaType.APPLICATION_JSON)
                // language=json
                .content("""
            {
              "isbn": "%s",
              "category": {
                "genre": "Comedy",
                "subGenre": "Absurdist"
              },
              "title": "The Hitchhiker's Guide to the Galaxy",
              "author": "Douglas Adams"
             }
            """.formatted(isbn))
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .hasNoNullFieldsOrProperties()
                .hasPath("isbn")
                .hasPath("title")
                .hasPath("author")
                .hasPath("createdAt")
                .hasPath("category")
                .hasPath("category.genre")
                .hasPath("category.subGenre");
        assertThatThrownBy(() -> keyValue().get(isbn))
                .isInstanceOf(IllegalArgumentException.class)
                .message()
                .isNotBlank();

        var ik = new IdempotentStore.IdempotentKey(isbn, "__NatsIdempotentController.create()");
        String encodedIsbn = NatsIdempotentStore.encodeIfNotValid(ik);
        assertThat(keyValue().get(encodedIsbn)).satisfies(kv -> assertThat(kv).isNotNull());
        assertRepetition(repetitionInfo);
        clearStoreIfLast(repetitionInfo);
    }

    @RepeatedTest(MAX_RETRIES)
    @Execution(ExecutionMode.CONCURRENT)
    void shouldUpdateBook(RepetitionInfo repetitionInfo) {
        NatsTestApplication.NatsIdempotentController.Book newBook =
                new NatsTestApplication.NatsIdempotentController.Book(
                        "978-0-547-92822-7",
                        new NatsTestApplication.NatsIdempotentController.Category("Fiction", "Fantasy"),
                        "The Hobbit",
                        "J.R.R. Tolkien",
                        Instant.now());
        getStore().putIfAbsent(UPDATE_ID, newBook);
        mvcTester
                .put()
                .uri("/nats/books/{id}", UPDATE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                // language=json
                .content("""
            {
             "isbn": "978-0-452-28423-4",
             "category": {
               "genre": "Science Fiction",
               "subGenre": "Dystopian"
             },
             "title": "1984-%s",
             "author": "George Orwell"
            }
            """.formatted(repetitionInfo.getCurrentRepetition()))
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .hasNoNullFieldsOrProperties()
                .hasPath("isbn")
                .hasPath("author")
                .hasPath("createdAt")
                .hasPath("category")
                .hasPath("category.genre")
                .hasPath("category.subGenre")
                .hasPathSatisfying("title", title -> assertThat(title).isEqualTo("1984-1"));
        assertRepetition(repetitionInfo);
        clearStoreIfLast(repetitionInfo);
    }

    @RepeatedTest(MAX_RETRIES)
    @Execution(ExecutionMode.CONCURRENT)
    void shouldChangeTitleBook(RepetitionInfo repetitionInfo) {
        NatsTestApplication.NatsIdempotentController.Book newBook =
                new NatsTestApplication.NatsIdempotentController.Book(
                        "978-0-547-92822-7",
                        new NatsTestApplication.NatsIdempotentController.Category("Fiction", "Fantasy"),
                        "The Hobbit",
                        "J.R.R. Tolkien",
                        Instant.now());
        getStore().putIfAbsent(CHANGE_TITLE_ID, newBook);
        mvcTester
                .patch()
                .uri("/nats/books/{id}", CHANGE_TITLE_ID)
                .queryParam("title", "The Silent Patient " + repetitionInfo.getCurrentRepetition())
                .contentType(MediaType.APPLICATION_JSON)
                .assertThat()
                .hasStatus(204)
                .hasHeader("X-Total-Count", "1")
                .body()
                .isEmpty();
        assertThat(getStore().get(CHANGE_TITLE_ID).title()).isEqualTo("The Silent Patient 1");
        clearStoreIfLast(repetitionInfo);
    }

    @RepeatedTest(MAX_RETRIES)
    @Execution(ExecutionMode.CONCURRENT)
    void shouldGenerateBooks(RepetitionInfo repetitionInfo) {
        mvcTester
                .post()
                .uri("/nats/books/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", GENERATED_KEY)
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .hasNoNullFieldsOrProperties()
                .hasPath("$[0].isbn")
                .hasPath("$[0].title")
                .hasPath("$[0].author")
                .hasPath("$[0].createdAt")
                .hasPath("$[0].category")
                .hasPath("$[0].category.genre")
                .hasPath("$[0].category.subGenre");

        assertRepetition(repetitionInfo);
        assertThat(getStore().size()).isEqualTo(3);
        clearStoreIfLast(repetitionInfo);
    }

    @Test
    void shouldUpdateInsteadCreateEntry() {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future<?> future = executor.submit(() -> mvcTester
                    .post()
                    .uri("/nats/books/heavy")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Idempotency-Key", "heavy-operation-id")
                    .assertThat()
                    .hasStatusOk());
            futures.add(future);
        }
        for (Future<?> future : futures) {
            assertDoesNotThrow(() -> future.get());
        }
    }
}
