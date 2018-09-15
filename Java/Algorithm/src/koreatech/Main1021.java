package koreatech;

import java.util.*;

public class Main1021 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            int size = scanner.nextInt();
            switch(size) {
            case 1: scanner.nextInt();
            case 0: System.out.println(size); break;
            default:
                int[] nums = new int[size];
                int max = 1;
                int length = 1;
                nums[0] = scanner.nextInt();
                for(int i = 1; i < size; ++i) {
                    nums[i] = scanner.nextInt();
                    if((nums[i-1] < nums[i]) && (nums[i-1]+1 == nums[i])) {
                        max = max>++length? max : length;
                    } else {
                        length = 1;
                    }
                }
                System.out.println(max);
            }            
        }
    }

}