import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "2\r\n"
            + "3 ABC\r\n"
            + "5 /HTP";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var testSize = Integer.parseInt(r.readLine());
        for (var testNum = 0; testNum < testSize; ++testNum) {
            var line = r.readLine().toCharArray();
            var printCount = line[0] - '0';
            for (var i = 2; i < line.length; ++i)
                for (var j = 0; j < printCount; ++j)
                    w.append(line[i]);
            w.append('\n');
        }
        w.flush();
    }
}
