import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class LombokConstructorExample {
    @NoArgsConstructor(force=true)
    static class A {
        final int a, b;
    }

    @AllArgsConstructor
    static class B {
        int a, b;
        @NonNull Object o;
    }

    @RequiredArgsConstructor(staticName="of")
    static class FinalEntry<K, V> implements Map.Entry<K, V> {
        @Getter private final K key;
        @Getter private final V value;

        @Override
        public V setValue(V value) {
            return this.value;
        }

    }

    public static void main(String[] args) {
        var a = new A();
        System.out.printf("A.a : %d, A.b : %d\n", a.a, a.b); // A.a : 0, A.b : 0
        try {
            new B(1, 2, null);
        } catch(Exception e) {
            System.out.println(e.getMessage()); // o is marked non-null but is null
        }
        var finalEntry = FinalEntry.of(1, new ArrayList<Integer>() {{ add(123); }});
        System.out.println(finalEntry.getValue().get(0)); // 123
    }

}