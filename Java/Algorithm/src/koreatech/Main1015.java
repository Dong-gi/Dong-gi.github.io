package koreatech;

import java.util.Scanner;
import java.util.Stack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class Main1015 {

    private static Stack<Character> stack = new Stack<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        scanner.nextLine();

        while(testCase-- > 0) {
            if(test(new StringBuilder(scanner.nextLine()))) {
                System.out.println("yes");
            } else {
                System.out.println("no");
            }
        }
    }

    private static boolean test(StringBuilder testString) {
        int length = testString.length();
        stack.clear();
        try {
            for(int i = 0; i < length; ++i) {
                switch(testString.charAt(i)) {
                case '(': case '[': case '{':
                    stack.push(testString.charAt(i));
                    break;
                case ')':
                    if(stack.pop() != '(') {
                        return false;
                    }
                    break;
                case ']':
                    if(stack.pop() != '[') {
                        return false;
                    }
                    break;
                case '}':
                    if(stack.pop() != '{') {
                        return false;
                    }
                    break;
                }
            }
        } catch(Exception e) {
            return false;
        }
        return stack.isEmpty();
    }

}