package regex;

import java.util.Scanner;
import java.util.regex.MatchResult;

public class MatchResultExample {

	public static void main(String[] args) {
		var scanner = new Scanner("a ab abc abcd");
		for(var result : scanner.findAll("(a)([\\w]+)").toArray()) {
			var mResult = (MatchResult) result;
			System.out.print(mResult.groupCount() + " : ");
			System.out.print(mResult.group(1) + "(" + mResult.start(1) + ":" + mResult.end(1) + "), ");
			System.out.print(mResult.group(2) + "(" + mResult.start(2) + ":" + mResult.end(2) + "), ");
			System.out.print(mResult.group(0) + "(" + mResult.start(0) + ":" + mResult.end(0) + "), ");
			System.out.println(mResult.group() + "(" + mResult.start() + ":" + mResult.end() + ")");
			/* Result
			2 : a(2:3), a(3:4), ab(2:4)
			2 : a(5:6), a(6:8), abc(5:8)
			2 : a(9:10), a(10:13), abcd(9:13)
			 */
		}
	}

}
