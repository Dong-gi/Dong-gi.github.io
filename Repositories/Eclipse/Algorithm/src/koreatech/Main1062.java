package koreatech;

import java.util.*;

public class Main1062 {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int testCase = in.nextInt();
        while(testCase-- > 0) {
            int num = in.nextInt();
            String result = "";
            for(int i = 0; i < 32; ++i) {
                switch(num>>i&1) {
                case 1:
                    result += i + " ";
                }
            }
            if(result.length() == 0) {
                System.out.println(-1);
            } else {
                System.out.println(result.trim());
            }
        }
    }
}
