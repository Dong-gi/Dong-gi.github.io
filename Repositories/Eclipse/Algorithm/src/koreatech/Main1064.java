package koreatech;

import java.util.*;

public class Main1064 {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int testCase = in.nextInt();
        while(testCase-- > 0) {
            int num = in.nextInt();
            int length = in.nextInt();
            int max = 0; 
            while(num-- > 0) {
                int pos = in.nextInt();
                int time = 0;
                if(in.nextInt() == 1) {
                    time = length - pos;
                } else {
                    time = pos;
                }
                max = max > time? max : time;
            }
            System.out.println(max);
        }
    }
}
