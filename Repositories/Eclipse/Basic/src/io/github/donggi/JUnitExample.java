package io.github.donggi;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JUnitExample {
    @BeforeAll
    static void beforeAll() {
        System.out.println("사전 작업 실행");
    }

    @BeforeEach
    void beforeEach() {}

    @Test
    void asserts() {
        assertTrue(true);
        assertFalse(false);
        assertEquals(1, 1);
        assertArrayEquals(new int[] {1}, new int[] {1});
    }

    @Test
    void timeout() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            while(true);
        });
    }

    @Test
    void exception() {
        assertThrows(NullPointerException.class, () -> {
            throw new NullPointerException();
        });
    }

    @AfterAll
    static void afterAll() {
        System.out.println("사후 작업 실행");
    }
}
