import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;

public class TypeTest {

    public static class Factory<T> {
        private final Supplier<T> supplier;
        private Class<T> type;

        public Factory(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @SuppressWarnings("unchecked")
        public T newInstance() {
            var result = supplier.get();
            if(type == null)
                type = (Class<T>) result.getClass();
            return result;
        }
        public Class<?> getType() {
            if(type == null) {
                synchronized(this) {
                    newInstance();
                }
            }
            return type;
        }
    }

    @Test
    public void factoryTest() {
        var factory = new Factory<List<Integer>>(() -> Arrays.asList(1));

        List<Integer> list = factory.newInstance();
        assertTrue(list.get(0) == 1);

        System.out.println(factory.getType().toGenericString());
        assertTrue(factory.getType().equals(Arrays.asList("str").getClass()));
    }

}
