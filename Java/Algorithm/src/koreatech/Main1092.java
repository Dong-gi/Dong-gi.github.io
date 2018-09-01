package koreatech;

import java.util.*;

public class Main1092 {

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = Integer.parseInt(in.nextLine().trim());

        for (int testCase = 0; testCase < numOfTestCase; ++testCase) {
            System.out.println(100 - in.nextInt());
        }
    }

}
