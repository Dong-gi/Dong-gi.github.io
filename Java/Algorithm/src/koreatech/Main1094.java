package koreatech;

import java.util.Scanner;

public class Main1094 {
	public static void main(String[] args) {
		final Scanner in = new Scanner(System.in);
		final int numOfTestCase = Integer.parseInt(in.nextLine());
		
		for(int testCase = 0; testCase < numOfTestCase; ++testCase) {
			// https://en.wikipedia.org/wiki/Self_number#Effective_test
			final int n = in.nextInt();
			boolean self = true;
			for(int i = 0; self && i < String.valueOf(n).length(); ++i) {
				self = sod(Math.abs(n - dr2(n) - 9*i)) != (dr2(n) + 9*i);
			}
			System.out.println(self? "Self" : "No");
		}
	}
	
	public static int sod(int n) {
		int sum = 0;
		for(char d : String.valueOf(n).toCharArray()) {
			sum += (d - '0');
		}
		return sum;
	}
	
	public static int dr(int n) {
		int sod = sod(n);
		return sod % 9 == 0? 9 : sod % 9;
	}
	
	public static int dr2(int n) {
		int dr = dr(n);
		return dr % 2 == 0? dr / 2 : (dr + 9) / 2;
	}
}