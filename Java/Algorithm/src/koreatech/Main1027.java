package koreatech;

import java.util.*;

public class Main1027 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            Stack<Character> stack = new Stack<>();
            for(Character c : scanner.next().toCharArray()) {
                if(stack.isEmpty()) stack.push(c);
                else if(stack.peek() == c) stack.pop();
                else stack.push(c);
            }
            StringBuilder builder = new StringBuilder();
            stack.stream().forEach(c -> builder.append(c));
            System.out.println(builder);
        }
    }
    
}