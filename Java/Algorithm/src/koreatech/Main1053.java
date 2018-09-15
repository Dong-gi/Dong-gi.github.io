package koreatech;

import java.util.*;

public class Main1053 {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int num = sc.nextInt();
        int[] nums = new int[num];
        for(int i=0; i<num; i++)
            nums[i] = sc.nextInt();
        
        Arrays.sort(nums);
        System.out.println(nums[num-1]);
    }
}