package time;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DurationTest {

    @Test
    void parseTest() {
        System.out.println(Duration.parse("P2DT12.345S"));      // PT48H12.345S
        System.out.println(Duration.parse("-P-2DT6H12.345S"));  // PT41H59M47.655S
    }

    @Test
    void withTest() {
        System.out.println(Duration.parse("PT1H2M3.004S")); // PT1H2M3.004S
        System.out.println(Duration.parse("PT1H2M3.004S").withSeconds(33)); // PT33.004S
        System.out.println(Duration.parse("PT1H2M3.004S").withNanos(999_999_999)); // PT1H2M3.999999999S
        System.out.println(Duration.parse("PT1H2M3.004S").withSeconds(33).withNanos(999_999_999)); // PT33.999999999S
    }
    
}
