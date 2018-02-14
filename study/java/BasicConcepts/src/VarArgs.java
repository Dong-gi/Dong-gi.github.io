
public class VarArgs {

	public static void main(String[] args) {
		System.out.println(sum(1, 2, 3, 4));
	}
	
	private static int sum(int... nums) {
		int result = 0;
		for(int n : nums) {
			result += n;
		}
		return result;
	}

}
