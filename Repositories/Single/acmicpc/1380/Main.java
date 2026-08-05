import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "3\r\n"
            + "Betty Boolean\r\n"
            + "Alison Addaway\r\n"
            + "Carrie Carryon\r\n"
            + "1 B\r\n"
            + "2 A\r\n"
            + "3 B\r\n"
            + "3 A\r\n"
            + "1 A\r\n"
            + "2\r\n"
            + "Helen Clark\r\n"
            + "Margaret Thatcher\r\n"
            + "1 B\r\n"
            + "2 B\r\n"
            + "2 A\r\n"
            + "0";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var lineIdx = 0;
        var scenarioNum = 0;
        while (true) {
            var line = lines.get(lineIdx++);
            var studentCount = Integer.parseInt(line);
            if (studentCount == 0)
                break;

            scenarioNum += 1;
            var studentNames = new String[studentCount];
            for (var i = 0; i < studentCount; ++i)
                studentNames[i] = lines.get(lineIdx++);
            var nameSet = new HashSet<String>();
            var testCount = studentCount * 2 - 1;
            for (var testNum = 0; testNum < testCount; ++testNum) {
                var studentNumber = Integer.parseInt(lines.get(lineIdx++).split(" ")[0]);
                var name = studentNames[studentNumber - 1];
                if (nameSet.remove(name) == false)
                    nameSet.add(name);
            }
            w.append(String.valueOf(scenarioNum)).append(' ').append(nameSet.iterator().next()).append('\n');
        }
        w.flush();
    }
}
