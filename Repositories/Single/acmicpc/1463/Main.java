import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "1000000";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var x = Integer.parseInt(r.readLine());
        var answers = new int[1000001];
        var count = 0;
        var queue = new LinkedList<>(Arrays.asList(1));
        while (queue.isEmpty() == false) {
            count += 1;
            var newQueue = new LinkedList<Integer>();
            for (var n : queue) {
                var newN = n * 3;
                if (newN <= 1000000 && answers[newN] == 0) {
                    answers[newN] = count;
                    newQueue.add(newN);
                }
                newN = n * 2;
                if (newN <= 1000000 && answers[newN] == 0) {
                    answers[newN] = count;
                    newQueue.add(newN);
                }
                newN = n + 1;
                if (newN <= 1000000 && answers[newN] == 0) {
                    answers[newN] = count;
                    newQueue.add(newN);
                }
            }
            queue = newQueue;
        }
        w.append(String.valueOf(answers[x]));
        w.flush();
    }
}
