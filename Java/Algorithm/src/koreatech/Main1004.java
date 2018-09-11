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
            String sumString = String.valueOf(Integer.parseInt(numberString) + reverse);
            System.out.println(sumString.equals(new StringBuilder(sumString).reverse().toString())?
                                    "yes" : "no");
        }
    }

}
