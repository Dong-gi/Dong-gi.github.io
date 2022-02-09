import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        var hour = scanner.nextInt();
        var minute = scanner.nextInt();

        var calendar = GregorianCalendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        calendar.add(Calendar.MINUTE, -45);
        System.out.printf("%d %d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }
}
