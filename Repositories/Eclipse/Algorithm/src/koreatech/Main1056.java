package koreatech;

import java.util.*;

public class Main1056 {

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = Integer.parseInt(in.nextLine().trim());

        int[] results = new int[101];
        results[0] = results[1] = 1;
        for(int i = 2; i <= 100; ++i)
            results[i] = (results[i-2] + results[i-1]) % 1_000_000_007;

        for (int testCase = 0; testCase < numOfTestCase; ++testCase)
            System.out.println(results[Integer.parseInt(in.nextLine().trim())]);
    }
}