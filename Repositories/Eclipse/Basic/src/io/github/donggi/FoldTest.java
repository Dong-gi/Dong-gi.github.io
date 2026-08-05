package io.github.donggi;

import static org.junit.Assert.*;

import java.util.function.BiFunction;
import org.junit.Test;

public class FoldTest {

    @Test
    public void test() {
        var list = new List<Integer>(0).add(10).add(1).add(9);
        assertTrue(list.length() == 4);
    }

}

class List<T> {
    T value;
    List<T> link;
    List<T> last;

    List(T init) {
        value = init;
    }
    List<T> add(T value) {
        if(link == null) {
            link = new List<T>(value);
            last = link;
        } else {
            last.link = new List<T>(value);
            last = last.link;
        }
        return this;
    }
    <V> V fold(List<T> list, V before, BiFunction<List<T>, V, V> function) {
        if(list == null)
            return before;
        return fold(list.link, function.apply(list, before), function);
    }
    int length() {
        return fold(this, 0, (list, before) -> before + 1);
    }
}