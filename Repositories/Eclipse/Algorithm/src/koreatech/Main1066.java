package koreatech;

import java.util.*;

public class Main1066 {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int testCase = in.nextInt();
        while(testCase-- > 0) {
            int year = in.nextInt();
            System.out.println(((year%400 == 0) || ((year%100 != 0) && (year%4 == 0)))?
                    "yes" : "no");
        }
    }
}
