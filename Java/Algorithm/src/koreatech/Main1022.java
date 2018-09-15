package koreatech;

import java.util.*;

public class Main1022 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            int n = scanner.nextInt();
            System.out.println(n%4 != 0? "win" : "lose");
        }
    }


}