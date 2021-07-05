import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Date;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;

import java.nio.ByteBuffer;

public class TOTP {

	// Power of 10. Just for fast reference.
	private static final int[] DIGITS_POWER = { 1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000 };

	private final String plainSecretKey = "test key 1234567890!";
	private final byte[] secretKey;
	private final String issuer = "issuer";
	private final String user = "user";
	private final String algorithm = "HmacSHA1";
	private final int digits = 6;
	private final Duration period = Duration.ofSeconds(30);

	private TOTP() {
		final var base32 = new Base32();
		var base32Key = base32.encodeAsString(plainSecretKey.getBytes());
		System.out.printf("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=%s&digits=%d&period=%d\n", issuer, user,
				base32Key, issuer, algorithm.substring(4), digits, period.toSeconds());
		secretKey = base32.decode(base32Key);
	}

	public static void main(String[] args) throws Exception {
		var totp = new TOTP();
		var beforeOTP = 0L;
		while (true) {
			var newOTP = totp.generateTOTP();
			if (beforeOTP != newOTP) {
				System.out.printf("%s : %06d\n", new Date(), newOTP);
				beforeOTP = newOTP;
			}
			Thread.sleep(1000);
		}
	}

	public long generateTOTP() throws GeneralSecurityException {
		var timeMsg = ByteBuffer.allocate(8).putLong(new Date().getTime() / period.toMillis()).array();
		var hmac = Mac.getInstance(algorithm);
		hmac.init(new SecretKeySpec(secretKey, "RAW"));
		var hash = hmac.doFinal(timeMsg);

		final int offset = hash[hash.length - 1] & 0x0f;

        return ((hash[offset]     & 0x7f) << 24 |
                (hash[offset + 1] & 0xff) << 16 |
                (hash[offset + 2] & 0xff) <<  8 |
                (hash[offset + 3] & 0xff)) %
                DIGITS_POWER[digits];
	}
}
