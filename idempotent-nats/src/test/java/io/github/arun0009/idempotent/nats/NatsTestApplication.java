package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.annotation.Idempotent;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.nats")
class NatsTestApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(NatsTestApplication.class)
                .main(NatsTestApplication.class)
                .bannerMode(Banner.Mode.OFF)
                .run(args);
    }

    @RestController
    @RequestMapping("/nats/books")
    class NatsIdempotentController {
        static Map<String, Book> STORE = new ConcurrentHashMap<>();

        private static void sleep() {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @PostMapping("/generate")
        @Idempotent(duration = "PT1M")
        List<Book> generateBooks() {
            sleep();
            List<Book> books = List.of(
                    new Book(
                            "978-0-547-92822-7",
                            new Category("Fiction", "Fantasy"),
                            "The Hobbit",
                            "J.R.R. Tolkien",
                            Instant.now()),
                    new Book(
                            "978-0-345-33970-1",
                            new Category("Fiction", "Fantasy"),
                            "The Fellowship of the Ring",
                            "J.R.R. Tolkien",
                            Instant.now()),
                    new Book(
                            "978-0-345-33971-8",
                            new Category("Fiction", "Fantasy"),
                            "The Two Towers",
                            "J.R.R. Tolkien",
                            Instant.now()));
            STORE.putAll(
                    books.stream().collect(toMap(ignored -> UUID.randomUUID().toString(), Function.identity())));
            return books;
        }

        @PostMapping
        @Idempotent(key = "#book.isbn", duration = "PT1M")
        Book create(@RequestBody Book book) {
            sleep();
            String id = UUID.randomUUID().toString();
            Book created = book.withCreatedAt();
            STORE.put(id, created);
            return created;
        }

        @PutMapping("{id}")
        @Idempotent(key = "#book.isbn", duration = "PT1M")
        Book update(@PathVariable String id, @RequestBody Book book) {
            sleep();
            Book bookUpdated = book.withCreatedAt();
            STORE.computeIfPresent(id, (k, v) -> bookUpdated);
            return bookUpdated;
        }

        @PatchMapping("/{id}")
        @Idempotent(key = "#id", duration = "PT5S")
        ResponseEntity<Void> changeTitle(@PathVariable String id, @RequestParam String title) {
            sleep();
            STORE.computeIfPresent(id, (k, v) -> v.withTitle(title));
            return ResponseEntity.noContent().header("X-Total-Count", "1").build();
        }

        @Idempotent
        @PostMapping("heavy")
        Book heavyOperation() throws InterruptedException {
            TimeUnit.SECONDS.sleep(1);
            return new Book(
                    "1234567890",
                    new Category("Fiction", "Mystery"),
                    "The Mystery of the Lost Key",
                    "Arun",
                    Instant.now());
        }

        record Book(String isbn, Category category, String title, String author, Instant createdAt) {
            Book withCreatedAt() {
                return new Book(isbn, category, title, author, Instant.now());
            }

            Book withTitle(String title) {
                return new Book(isbn, category, title, author, Instant.now());
            }
        }

        record Category(String genre, String subGenre) {}
    }
}
