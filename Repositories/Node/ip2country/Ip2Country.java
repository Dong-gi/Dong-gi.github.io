import java.io.*;
import java.util.stream.Collectors;

public class Ip2Country {

    private static Object[][] mappings = new Object[0][0];

    static {
        try (var r = new BufferedReader(new FileReader("mapping.txt"))) {
            //new InputStreamReader(Ip2Country.class.getClassLoader().getResourceAsStream("mapping.txt")))) {
            var lines = r.lines().collect(Collectors.toList());
            var size = lines.size() / 2;
            mappings = new Object[size][2];
            for (var i = 0; i < size; ++i) {
                mappings[i][0] = Long.valueOf(lines.get(i * 2)).intValue();
                mappings[i][1] = lines.get(i * 2 + 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println(toCountryCode("1.1.1.1"));
        System.out.println(toCountryCode("8.8.8.8"));
        System.out.println(toCountryCode("123.123.123.123"));
        System.out.println(toCountryCode("200.200.200.200"));
    }

    public static String toCountryCode(String ipv4) {
        try {
            return toCountryCode(toIntIP(ipv4));
        } catch (Exception e) {
            return "??";
        }
    }

    public static String toCountryCode(int intIP) {
        if (mappings.length < 1)
            return "??";

        // [idx1, idx2)
        var idx1 = 0;
        var idx2 = mappings.length;
        while (idx1 <= idx2) {
            var mid = (idx1 + idx2) >>> 1;
            if (mid <= idx1)
                return (String) mappings[mid][1];
            if (Integer.compareUnsigned((Integer) mappings[mid][0], intIP) > 0)
                idx2 = mid;
            else if (Integer.compareUnsigned((Integer) mappings[mid][0], intIP) < 0)
                idx1 = mid;
            else
                return (String) mappings[mid][1];
        }
        return (String) mappings[idx1][1];
    }

    public static int toIntIP(String ipv4) {
        var parts = ipv4.split("\\.");
        return ((((Integer.parseInt(parts[0]) * 256) + Integer.parseInt(parts[1])) * 256) + Integer.parseInt(parts[2]))
            * 256 + Integer.parseInt(parts[3]);
    }

}
