package io.github.donggi;

enum Weekday {
    SUNDAY(0) {
        @Override
        void method() {
        };
    },
    FRIDAY(12), SATURDAY(-1);

    private final int num;

    /* private */ Weekday(int num) {
        this.num = num;
    }

    int getNum() { return num; }

    void method() {}
}

public class EnumExample {

    public static void main(String[] args) {
        var day = Weekday.FRIDAY;
        System.out.println(day.name());
        // toString() : 기본적으로 정의된 상수 이름을 반환한다.
        System.out.println(day);
        // ordinal() : 정의된 순번을 반환한다.
        System.out.println(day.ordinal());
        // compareTo() : ordinal()값을 비교한다.
        System.out.println(day.compareTo(Weekday.SATURDAY));
        // valueOf : 정의된 상수를 반환한다.
        System.out.println(Enum.valueOf(Weekday.class, "SUNDAY"));
    }

}
