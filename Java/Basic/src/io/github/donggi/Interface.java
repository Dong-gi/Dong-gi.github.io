package io.github.donggi;

interface Flyable {
    class Wing {}

    Wing LEFT_WING = new Wing();
    Wing RIGHT_WING = new Wing();

    void fly();

    default void flyFast() {
        System.out.println("빠르게 날기");
    }

    static int numberOfWings() {
        return 2;
    }
}

interface Flyable2 extends Flyable {
    Flyable.Wing BACK_WING = new Flyable.Wing();

    // @Override : 인터페이스의 static 메서드는 재정의 불가
    static int numberOfWings() {
        return 3;
    }
}

public class Interface implements Flyable {
    // @Override : 인터페이스의 static 메서드는 재정의 불가
    static int numberOfWings() {
        return 3;
    }

    public static void main(String[] args) {
        var w = new Flyable.Wing();
        new Interface().fly();
        new Interface().flyFast();
        System.out.println(Flyable.numberOfWings());
    }

    @Override
    public void fly() {
        System.out.println("날기 싫어");
    }

}