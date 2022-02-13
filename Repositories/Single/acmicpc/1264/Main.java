import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "How are you today?\r\n"
            + "Quite well, thank you, how about yourself?\r\n"
            + "I live at number twenty four.\r\n"
            + "#";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var limit = lines.size() - 1;

        for (var i = 0; i < limit; ++i) {
            var count = 0;
            for (var ch : lines.get(i).toCharArray()) {
                switch (ch) {
                    case 'a', 'e', 'i', 'o', 'u', 'A', 'E', 'I', 'O', 'U':
                        count += 1;
                }
            }
            w.append(String.valueOf(count)).append('\n');
        }
        w.flush();
    }
}
