package time;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ClockTest {

    @Test
    void tickTest() {
        var now = Clock.fixed(Clock.systemDefaultZone().instant(), ZoneId.systemDefault());
        System.out.println(now.instant()); // 2019-10-10T07:43:05.400088500Z
        System.out.println(Clock.tick(now, Duration.ofNanos(1)).instant()); // 2019-10-10T07:43:05.400088500Z
        System.out.println(Clock.tick(now, Duration.ofMillis(1)).instant()); // 2019-10-10T07:43:05.400Z
        System.out.println(Clock.tick(now, Duration.ofSeconds(1)).instant()); // 2019-10-10T07:43:05Z
        System.out.println(Clock.tick(now, Duration.ofMinutes(1)).instant()); // 2019-10-10T07:43:00Z
        System.out.println(Clock.tick(now, Duration.ofHours(1)).instant()); // 2019-10-10T07:00:00Z
    }

    @Test
    void withZoneTest() {
        var currentClock = Clock.systemDefaultZone();
        var newYorkClock = currentClock.withZone(ZoneId.of("America/New_York"));
        System.out.printf("Current tz : %s, time : %s\n", currentClock.getZone(), currentClock.instant());
        // Current tz : Asia/Seoul, time : 2019-10-10T07:51:32.296628700Z
        System.out.printf("NewYork tz : %s, time : %s\n", newYorkClock.getZone(), newYorkClock.instant());
        // NewYork tz : America/New_York, time : 2019-10-10T07:51:32.299557200Z
    }
    
}
