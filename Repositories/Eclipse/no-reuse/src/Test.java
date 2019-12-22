import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class Test {

    public static final String key = "key";

    public static void main(String[] args) throws IOException {
        String text;
          text = String.join("", Files.lines(Path.of("D:\\Game\\SteamLibrary\\steamapps\\common\\SlayTheSpire\\saves\\1_IRONCLAD.autosave")).toArray(String[]::new));
          try {
              System.out.print(decode(text));
          } catch(Exception e) {}
          text = String.join("\n", Files.lines(Path.of("D:\\Game\\SteamLibrary\\steamapps\\common\\SlayTheSpire\\saves\\1_IRONCLAD.autosave")).toArray(String[]::new));
          System.out.print(encode(text));
    }

    public static String encode(final String s) {
        return base64Encode(xorWithKey(s.getBytes(), key.getBytes()));
    }

    public static String decode(final String s) {
        return new String(xorWithKey(base64Decode(s), key.getBytes()));
    }

    private static byte[] xorWithKey(final byte[] a, final byte[] key) {
        final byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; ++i) {
            out[i] = (byte)(a[i] ^ key[i % key.length]);
        }
        return out;
    }

    private static byte[] base64Decode(final String s) {
        return Base64.getDecoder().decode(s);
    }

    private static String base64Encode(final byte[] bytes) {
        return new String(Base64.getEncoder().encode(bytes));
    }

    public static boolean isObfuscated(final String data) {
        return !data.contains("{");
    }

}
