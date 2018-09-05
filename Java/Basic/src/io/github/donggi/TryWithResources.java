package io.github.donggi;

public class TryWithResources {
    static class MyAutoCloseable implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("MyAutoCloseable::close");
        }
    }

    public static void main(String[] args) throws Exception {
        // 아주 먼 옛날
        MyAutoCloseable my = new MyAutoCloseable();
        try {
        } finally {
            my.close();
        }

        // 자바 1.7
        try (MyAutoCloseable in = new MyAutoCloseable()) {}

        // 자바 9
        MyAutoCloseable my2 = new MyAutoCloseable();
        try (my2; MyAutoCloseable my3 = new MyAutoCloseable()) {}

        // 자바 10
        try (var my3 = new MyAutoCloseable()) {}
    }

}
