import java.util.Scanner;

@FunctionalInterface
interface IntPredicate{
	boolean predicate(int n);
}

public class FunctionalInterfaceTest {
	public static void main(String[] args) {
		IntPredicate isEven = n -> (n % 2 == 0);
		Scanner in = new Scanner(System.in);
		System.out.print("정수 입력: ");
		int n = in.nextInt();
		if(isEven.predicate(n)) System.out.println("짝수");
		else System.out.println("홀수");
	}
}
