package koreatech;

import java.util.Scanner;

public class Main1004 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        scanner.nextLine();
        while(testCase-- > 0) {
            String numberString = scanner.nextLine();
            int reverse = Integer.parseInt(new StringBuilder(numberString).reverse().toString());
            String sumString = "" + (Integer.parseInt(numberString) + reverse);
            if(sumString.equals(new StringBuilder(sumString).reverse().toString())) {
                System.out.println("yes");
            } else {
                System.out.println("no");
            }
        }
    }

}
