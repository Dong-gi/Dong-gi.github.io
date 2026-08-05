import java.util.Arrays;

class SwitchExpression {
    private static enum Day {
        MONDAY, FRIDAY, SUNDAY, TUESDAY, THURSDAY, SATURDAY, WEDNESDAY,
    }

    public static void main(String[] args) {
        Arrays.stream(Day.values()).forEach(SwitchExpression::printDay);
        // MONDAY : Weekday
        // FRIDAY : Weekday
        // I love SUNDAY : Weekend
        // TUESDAY : Weekday
        // THURSDAY : Weekday
        // I love SATURDAY : Weekend
        // WEDNESDAY : Weekday

        System.out.println(nthDayFromSunday(Day.MONDAY)); // 1
        System.out.println(nthDayFromMonday(Day.FRIDAY)); // 4
    }

    public static void printDay(Day day) {
        switch(day) {
            case SATURDAY, SUNDAY -> {
                System.out.print("I love ");
                System.out.println(day.name() + " : Weekend");
            }
            default -> System.out.println(day.name() + " : Weekday");
        }
    }

    public static int nthDayFromSunday(Day day) {
        return switch(day) {
            case SUNDAY: yield 0;
            case MONDAY: yield 1;
            case TUESDAY: yield 2;
            case WEDNESDAY: yield 3;
            case THURSDAY: yield 4;
            case FRIDAY: yield 5;
            case SATURDAY: yield 6;
        };
    }

    public static int nthDayFromMonday(Day day) {
        return switch(day) {
            case SUNDAY -> 6;
            case MONDAY -> 0;
            case TUESDAY -> 1;
            case WEDNESDAY -> 2;
            case THURSDAY -> 3;
            case FRIDAY -> 4;
            case SATURDAY -> 5;
        };
    }
}