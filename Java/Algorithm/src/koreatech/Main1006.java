package koreatech;

import java.util.Scanner;

import java.util.ArrayList;
import java.util.HashSet;

public class Main1006 {

    private static int change;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            change = -(scanner.nextInt()-scanner.nextInt());
            System.out.printf(
                    "%d %d %d %d %d %d %d %d\n",
                    give(50000),
                    give(10000),
                    give(5000),
                    give(1000),
                    give(500),
                    give(100),
                    give(50),
                    give(10)
                    );
        }
    }

    private static int give(int unit) {
        int result = change / unit;
        change %= unit;
        return result;
    }

}