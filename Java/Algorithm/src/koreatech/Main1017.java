package koreatech;

import java.util.Scanner;
import java.util.Stack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class Main1017 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            int moneyCount = scanner.nextInt();
            int[] money = new int[moneyCount+(3-moneyCount%3)%3];
            for(int i = 0; i < moneyCount; ++i) {
                money[i] = scanner.nextInt();
            }
            int cycle = money.length/3;
            int[][] sum = new int[cycle+1][3];
            for(int i = 1; i <= cycle; ++i) {
                sum[i][0] = max(sum[i-1][0], sum[i-1][1], sum[i-1][2]) + money[3*i-2] + money[3*i-1];
                sum[i][1] = max(sum[i-1][0], sum[i-1][1] + money[3*i-3], sum[i-1][2] + money[3*i-3]) + money[3*i-1];
                sum[i][2] = max(sum[i-1][0] + money[3*i-2], sum[i-1][1] + max(money[3*i-3], money[3*i-2]), sum[i-1][2] + money[3*i-3] + money[3*i-2]);
            }
            System.out.println(max(sum[cycle][0], sum[cycle][1], sum[cycle][2]));
        }
    }
    
    private static int max(int... nums) {
        Arrays.sort(nums);
        return nums[nums.length-1];
    }

}