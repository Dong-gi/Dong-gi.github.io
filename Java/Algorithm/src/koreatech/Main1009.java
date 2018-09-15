package koreatech;

import java.util.*;

public class Main1009 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();

        while(testCase-- > 0){
            int target = scanner.nextInt();
            int result = 0;
            for(int i=0; i<4; ++i){
                result <<= 8;
                result += target & 0xff;
                target >>= 8;
            }
            System.out.println(result);
        }
    }

}