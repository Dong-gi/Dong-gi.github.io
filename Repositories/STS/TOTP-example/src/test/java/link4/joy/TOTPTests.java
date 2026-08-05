package link4.joy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.*;

public class TOTPTests {
    @Test
    void test() throws Exception {
        var totp = new TOTP();
        var beforeOTP = 0L;
        while (true) {
            var newOTP = totp.generateTOTP();
            assertTrue(newOTP >= 0);
            if (beforeOTP != newOTP) {
                Logger.getGlobal().log(Level.INFO, "%06d".formatted(newOTP));
                beforeOTP = newOTP;
            }
            Thread.sleep(1000);
        }
    }
}