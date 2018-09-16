package koreatech;

import java.util.*;

public class Main1067 {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int testCase = in.nextInt();
        in.nextLine();
        while(testCase-- > 0) {
            String str = in.nextLine().toLowerCase();
            int[] nums = new int[26];
            for(int i = 0; i < str.length(); ++i) {
                nums[str.charAt(i)-'a']++;
            }
            int result = 0;
            boolean hasOdd = false;
            for(int i = 0; i < 26; ++i) {
                result += nums[i]/2 * 2;
                hasOdd = hasOdd || nums[i]%2 != 0;
            }
            System.out.println(result + (hasOdd? 1 : 0));
        }
    }
}
