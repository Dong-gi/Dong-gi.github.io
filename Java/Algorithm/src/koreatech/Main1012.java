package koreatech;

import java.util.Scanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Main1012 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            int length = scanner.nextInt();
            int[] nums = new int[length];
            for(int i = 0; i < length; ++i) {
                nums[i] = scanner.nextInt();
            }
            Arrays.sort(nums);
            
            System.out.print(nums[0]);
            for(int i = 1; i < nums.length; ++i) {
                System.out.print(" " + nums[i]);
            }
            System.out.println();
        }
    }

}