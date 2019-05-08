package koreatech;

import java.util.*;
import java.util.stream.Stream;

public class Main1086 {

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = Integer.parseInt(in.nextLine().trim());

        for (int testCase = 0; testCase < numOfTestCase; ++testCase) {
            final String str = in.nextLine();
            Optional<Integer> result = Stream.iterate(1, i -> i + 1).filter(i -> customHash(str + ' ' + i)).findAny();
            System.out.println(result.get());
        }
    }

    public static boolean customHash(String str) {
        long hash = 5381;
        for(char c : str.toCharArray())
              hash = ((hash << 5) + hash) + c;
        return (int)(hash & 0x7FFFFFFF) % 10000 == 0;
     }
}
