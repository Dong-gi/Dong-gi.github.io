package koreatech;

import java.util.*;

public class Main1107 {

	public static void main(String[] args) {
		final Scanner in = new Scanner(System.in);
		final int numOfTestCase = Integer.parseInt(in.nextLine());

		for(int testCase = 0; testCase < numOfTestCase; ++testCase) {
			String original = in.nextLine();
			System.out.println(original.length() - original.replaceAll("[aeiou]", "").length());
		}
	}

}
