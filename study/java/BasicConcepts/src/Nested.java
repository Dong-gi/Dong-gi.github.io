interface Actable {
    default void act() {
        System.out.println("Outer Interface");
    }
}

public class Nested {
    // 정적 멤버 클래스
    static class Inner1 {
        public void print() {
            System.out.println("Static Nested Class");
        }
    }

    // 비정적 멤버 클래스
    class Inner2 {
        public void print() {
            System.out.println("Non-Static Nested Class");
        }
    }

    // 클래스 내의 인터페이스
    /*자동으로 static*/ interface Actable {
        default void act() {
            System.out.println("Inner Interface");
        }
    }

    public static void main(String[] args) {
        var in1 = new Nested.Inner1();
        var nested = new Nested();
        var in2 = nested.new Inner2();

        // anonymous class
        // 자신을 둘러싸고 있는 영역의 final 변수를 이용할 수 있다.
        in1 = new Inner1() {
            @Override
            public void print() {
                System.out.println("New Print");
            }
        };
        in1.print();

        // Main.Actable이 참조된다.
        new Actable() {
            private String msg = "Hello World";
            @Override
            public void act() {
                System.out.println(msg);
            }
        }.act();

        // local class
        // 자신을 둘러싸고 있는 영역의 final 변수를 이용할 수 있다.
        final class LocalClass implements Actable {}
        new LocalClass().act();
    }
}