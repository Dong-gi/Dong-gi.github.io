package x;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppTest {
    @BeforeAll
    static void beforeAll() {
        System.out.println("Before All");
    }

    @AfterAll
    static void afterAll() {
        System.out.println("After All");
    }

    @BeforeEach
    void beforeEach() {
        System.out.println("Before Each");
    }

    @AfterEach
    void afterEach() {
        System.out.println("After Each");
    }

    @Test
    void asserts() {
        assertTrue(true);
        assertFalse(false);
        assertEquals(1, 1);
        assertArrayEquals(new int[] { 1 }, new int[] { 1 });
    }

    @Test
    void appHasAGreeting() {
        App classUnderTest = new App();
        assertNotNull(classUnderTest.getGreeting(), "app should have a greeting");
    }

    @Test
    void timeout() {
        final var t = Thread.currentThread();
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertTrue(t != Thread.currentThread()); // Run in different thread
        });
    }

    @Test
    void exception() {
        assertThrows(NullPointerException.class, () -> {
            throw new NullPointerException();
        });
    }
}