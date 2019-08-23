package util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MapExample {

    @Test
    void getOrDefaultTest() {
        var map = Map.<Integer, Integer>of();
        assertTrue(map.getOrDefault(0, 10) == 10); // OK
    }

    @Test
    void putIfAbsentTest() {
        var map = new HashMap<Integer, Integer>();
        map.put(0, 0);
        map.putIfAbsent(0, 10);
        assertTrue(map.getOrDefault(0, 10) == 0); // OK
    }

    @Test
    void computeTest() {
        var map = new HashMap<Integer, Integer>();
        map.put(0, 0);
        map.compute(0, (key, value) -> value + 1);
        assertTrue(map.get(0) == 1); // OK
    }

    @Test
    void computeIfTest() {
        var map = new HashMap<Integer, Integer>();
        map.put(0, 0);
        map.put(1, null);

        assertTrue(map.computeIfAbsent(0, (key) -> 10) == 0);       // OK
        assertTrue(map.computeIfAbsent(1, (key) -> 20) == 20);      // OK
        assertTrue(map.computeIfAbsent(2, (key) -> null) == null);  // OK

        assertTrue(map.get(0) == 0);        // OK
        assertTrue(map.get(1) == 20);       // OK
        assertFalse(map.containsKey(2));    // OK

        map.put(1, null);
        assertTrue(map.computeIfPresent(0, (key, value) -> value + 1) == 1);    // OK
        assertTrue(map.computeIfPresent(1, (key, value) -> value + 1) == null); // OK
        assertTrue(map.computeIfPresent(2, (key, value) -> value + 1) == null); // OK

        assertTrue(map.get(0) == 1);        // OK
        assertTrue(map.get(1) == null);     // OK
        assertFalse(map.containsKey(2));    // OK
    }

    @Test
    void replaceTest() {
        var map = new HashMap<Integer, Integer>();
        map.put(0, 0);
        map.put(1, 1);
        
        assertTrue(map.replace(0, 1) == 0); // OK
        assertFalse(map.replace(1, 0, 2));  // OK
        
        map.replaceAll((key, value) -> value + 1);
        assertTrue(map.get(0) == 2);        // OK
        assertTrue(map.get(1) == 2);        // OK
    }
    
    @Test
    void mergeTest() {
        var map = new HashMap<Integer, Integer>();
        map.put(0, 0);
        map.put(1, null);
        map.put(2, 2);
        
        final var newValue = 1234567;
        map.merge(0, newValue, (oldVal, newVal) -> oldVal + 1);
        map.merge(1, newValue, (oldVal, newVal) -> newVal * 2);
        map.merge(2, newValue, (oldVal, newVal) -> null);
        map.merge(3, newValue, (oldVal, newVal) -> null);
        
        assertTrue(map.get(0) == 1);        // OK
        assertTrue(map.get(1) == newValue); // OK
        assertFalse(map.containsKey(2));    // OK
        assertTrue(map.get(3) == newValue); // OK
    }
}
