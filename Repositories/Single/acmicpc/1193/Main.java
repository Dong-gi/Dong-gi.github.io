import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "14";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var x = Integer.parseInt(lines.get(0));

        var diagonalCount = 1;
        for (; diagonalCount < x; ++diagonalCount)
            x -= diagonalCount;

        int bunja;
        int bunmo;
        if (diagonalCount % 2 == 0) {
            bunmo = diagonalCount + 1 - x;
            bunja = diagonalCount + 1 - bunmo;
        } else {
            bunja = diagonalCount + 1 - x;
            bunmo = diagonalCount + 1 - bunja;
        }
        w.append(String.valueOf(bunja)).append('/').append(String.valueOf(bunmo));
        w.flush();
    }
}
