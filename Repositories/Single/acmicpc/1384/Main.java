import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "5\r\n"
            + "Ann P N P P\r\n"
            + "Bob P P P P\r\n"
            + "Clive P P P P\r\n"
            + "Debby P N P P\r\n"
            + "Eunice P P P P\r\n"
            + "6\r\n"
            + "Zheng P P P P P\r\n"
            + "Yeng P P P P P\r\n"
            + "Xiao P P P P P\r\n"
            + "Will P P P P P\r\n"
            + "Veronica P P P P P\r\n"
            + "Utah P P P P P\r\n"
            + "0";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var lineIdx = 0;
        var groupNum = 0;
        while (true) {
            var childCount = Integer.parseInt(lines.get(lineIdx++));
            if (childCount < 1)
                break;

            groupNum += 1;
            var names = new String[childCount];
            var codeStrings = new String[childCount];
            var nastyStrings = new LinkedList<String>();

            for (var i = 0; i < childCount; ++i) {
                var line = lines.get(lineIdx++);
                var parts = line.split(" ", 2);
                names[i] = parts[0];
                codeStrings[i] = parts[1];
            }
            for (var i = 0; i < childCount; ++i) {
                var codes = codeStrings[i].split(" ");
                for (var j = 0; j < codes.length; ++j) {
                    if (codes[j].charAt(0) == 'N')
                        nastyStrings.add("%s was nasty about %s\n"
                            .formatted(names[(childCount + i - 1 - j) % childCount], names[i]));
                }
            }

            if (groupNum > 1)
                w.append('\n');
            w.append("Group ").append(String.valueOf(groupNum)).append('\n');
            if (nastyStrings.isEmpty())
                w.append("Nobody was nasty\n");
            for (var str : nastyStrings)
                w.append(str);
        }
        w.flush();
    }
}
