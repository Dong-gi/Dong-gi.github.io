package io.github.donggi;

public class VarArgs {
	public static void main(String[] args) {
		System.out.println(sum(1, 2, 3, 4));
		System.out.println(sum(new int[] {1, 2, 3, 4}));
	}
	
	private static int sum(int... nums) {
		var result = 0;
		for(var n : nums) {
			result += n;
		}
		return result;
	}
}
