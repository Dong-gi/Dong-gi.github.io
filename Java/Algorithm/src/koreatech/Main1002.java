package koreatech;

import java.util.Scanner;

public class Main1002 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        scanner.nextLine();
        while(testCase-- > 0) {
            System.out.println(scanner.nextLine().substring(2, 4));
        }
    }

}
