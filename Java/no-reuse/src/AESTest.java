import java.io.*;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.util.Arrays;
import java.util.Base64;

public class AESTest {
	private static final String TYPE = "AES";
	private static final Key KEY;
	private static Cipher ENCRYPTER;
	private static Cipher DECRYPTER;

	static {
		KEY = new SecretKeySpec("1$23cf!V@Fo2v1*i".getBytes(), TYPE);
		try {
			ENCRYPTER = Cipher.getInstance(TYPE);
			ENCRYPTER.init(Cipher.ENCRYPT_MODE, KEY);
			DECRYPTER = Cipher.getInstance(TYPE);
			DECRYPTER.init(Cipher.DECRYPT_MODE, KEY);
		} catch (Exception e) { e.printStackTrace(); }
	}

	public static String encrypt(String data) throws Exception {
		return Base64.getEncoder().encodeToString(ENCRYPTER.doFinal(data.getBytes()));
	}

	public static byte[] encrypt(byte[] data) throws Exception {
		return ENCRYPTER.doFinal(Base64.getEncoder().encode(data));
	}

	public static String decrypt(String encryptedData) throws Exception {
		return new String(DECRYPTER.doFinal(Base64.getDecoder().decode(encryptedData)));
	}

	public static byte[] decrypt(byte[] data) throws Exception {
		return Base64.getDecoder().decode(DECRYPTER.doFinal(data));
	}

	public static void main(String[] args) throws Exception {
		byte[] d = "hello".getBytes();
		System.out.println(d.length);
		System.out.println(Arrays.toString(d));
		System.out.println(encrypt(d).length);
		System.out.println(Arrays.toString(encrypt(d)));
		BufferedInputStream in = new BufferedInputStream(System.in);
		int datagramLength = in.available();
		byte[] buffer = new byte[datagramLength];
		if(datagramLength > 0) {
			in.read(buffer, 0, datagramLength);
			//System.out.println("Datagram Length : " + datagramLength);
			int encryptedLength = 0;
			encryptedLength |= ((int)buffer[0] << 24 >>> 24);
			encryptedLength <<= 8;
			encryptedLength |= ((int)buffer[1] << 24 >>> 24);
			encryptedLength <<= 8;
			encryptedLength |= ((int)buffer[2] << 24 >>> 24);
			encryptedLength <<= 8;
			encryptedLength |= ((int)buffer[3] << 24 >>> 24);
			//System.out.println("size : " + encryptedLength);
			if(encryptedLength > 0) {
				byte[] data = new byte[encryptedLength];
				System.arraycopy(buffer, 4, data, 0, encryptedLength);
				//System.out.println(Arrays.toString(data));
				System.out.write(decrypt(data));
			} else if(encryptedLength == 0) {
				byte[] data = new byte[datagramLength - 4];
				System.arraycopy(buffer, 4, data, 0, data.length);
				//System.out.println(Arrays.toString(data));
				data = encrypt(data);
				byte[] merge = new byte[data.length + 4];
				merge[0] |= data.length >> 24;
	            merge[1] |= data.length >> 16;
	            merge[2] |= data.length >> 8;
	            merge[3] |= data.length;
	            System.arraycopy(data, 0, merge, 4, data.length);
				System.out.write(merge);
			}
			System.out.flush();
		}
	}

}