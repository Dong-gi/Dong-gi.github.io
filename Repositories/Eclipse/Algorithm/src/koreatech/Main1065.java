package koreatech;

import java.util.*;

public class Main1065 {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int testCase = in.nextInt();
        in.nextLine();
        while(testCase-- > 0) {
            String before = in.nextLine().toLowerCase();
            before = before.replaceAll("[\\W\\d]", "");
            System.out.println(before.equals(new StringBuilder(before).reverse().toString()));
        }
    }
}
