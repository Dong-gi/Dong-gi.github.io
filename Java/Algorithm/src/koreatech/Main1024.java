package koreatech;

import java.util.*;

public class Main1024 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String str = scanner.nextLine();
        String[] nums = str.split("[+-]");
        if(nums.length == 1) {
            System.out.println(Integer.parseInt(nums[0]));
            return;
        }
        
        ArrayList<String> numList = new ArrayList<>();
        Collections.addAll(numList, nums);
        int result = Integer.parseInt(numList.remove(0));
        
        for(int i = 0; i < str.length(); ++i) {
            switch(str.charAt(i)) {
            case '+':
                result += Integer.parseInt(numList.remove(0));
                break;
            case '-':
                result -= Integer.parseInt(numList.remove(0));
                break;
            }
        }
        System.out.println(result);
    }
    
}