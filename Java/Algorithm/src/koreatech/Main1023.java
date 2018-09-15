package koreatech;

import java.util.*;

public class Main1023 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            int num = scanner.nextInt();
            String str = "-";
            if(num%3 == 0)
                str = "Fizz";
            if(num%5 == 0)
                str = "Buzz";
            if(num%15 == 0)
                str = "Fizz Buzz";
            System.out.println(str);
        }
    }
    
}