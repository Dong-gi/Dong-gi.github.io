package io.github.donggi;

import java.util.Arrays;

public class SwitchExpression {

    private static enum Day {
        MONDAY, FRIDAY, SUNDAY, TUESDAY, THURSDAY, SATURDAY, WEDNESDAY,       
    }
    
    public static void main(String[] args) {
        Arrays.stream(Day.values()).forEach(SwitchExpression::printDay);
        int n = nthDayFromSunday(Day.MONDAY);
        n = nthDayFromMonday(Day.FRIDAY);
    }

    public static void printDay(Day day) {
        switch(day) {
        case SATURDAY, SUNDAY -> System.out.println(day.name() + " : Weekends");
        default -> System.out.println(day.name() + " : Weekdays");
        }
    }
    
    public static int nthDayFromSunday(Day day) {
        return switch(day) {
        case SUNDAY: break 0;
        case MONDAY: break 1;
        case TUESDAY: break 2;
        case WEDNESDAY: break 3;
        case THURSDAY: break 4;
        case FRIDAY: break 5;
        case SATURDAY: break 6;
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
