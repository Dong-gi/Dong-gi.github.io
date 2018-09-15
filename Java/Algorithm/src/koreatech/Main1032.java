package koreatech;

import java.util.*;

public class Main1032 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        switch(scanner.nextInt()) {
        case 6: case 28: case 496: case 8128: case 33550336:
            // case 8589869056: case 137438691328:
            System.out.println("perfect");
            break;
        default: System.out.println("no perfect");
        }
    }
    
}