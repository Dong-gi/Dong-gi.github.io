package koreatech;

import java.util.*;

public class Main1068 {

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = in.nextInt();

        // 1: 1 ~ 9 : 9개
        // 2: 10 ~ 99 : 90개
        // 3: 100 ~ 999 : 900개
        HashMap<Integer, Long> levels = new HashMap<>() {{ put(1, 9L); }};
        int level = 2;
        int num = 90;
        while(true) {
            long sum = level * (long)num + levels.get(level-1);
            //System.out.print(sum + " , ");
            levels.put(level, sum);
            if(sum > 2147483647) break;
            level += 1;
            num *= 10;
        }

        for(int testCase = 0; testCase < numOfTestCase; ++testCase) {
            num = in.nextInt();
            for(level=1; ;++level) {
                if(num <= levels.get(level))
                    break;
            }
            int quotient = (int) (num - levels.getOrDefault(level-1, 0L)) / level;
            int remainder = (int) (num - levels.getOrDefault(level-1, 0L)) % level;
            num = (int) Math.pow(10, level-1) - 1 + quotient;
            //System.out.println("q : " + quotient + ", r : " + remainder);
            if(quotient == 0 || remainder > 0)
                num += 1;
            //System.out.println(num);
            System.out.println(String.valueOf(num).charAt(remainder == 0? level-1 : remainder-1));
        }

    }
}
