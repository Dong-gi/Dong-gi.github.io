import java.util.*;

public class Generic2 {
	// Generic Constructor
	public <T> Generic2(T arg) {}
	
	// 비제네릭 클래스 - 제네릭 클래스 사이의 상속 가능
	class Generic3<T> extends Generic2 {
		public Generic3(T arg) {
			super(arg);
		}
	}
	
	public static void main(String[] args) {
		ArrayList<String> l1 = new ArrayList<>();
		ArrayList<Integer> l2 = new ArrayList<>();
		l2.add(1);
		
		System.out.println(isEmpty(l1));
		System.out.println(isEmpty(l2));
		// System.out.println(hasNumber(l1));
		System.out.println(hasNumber(l2));
	}

	public static boolean isEmpty(Collection<?> c) {
		return c.isEmpty();
	}
	
	public static boolean hasNumber(Collection<? extends Number> c) {
		return !c.isEmpty();
	}
	
	public static <T extends Number> boolean hasNumber2(Collection<T> c) {
		return !c.isEmpty();
	}
	
}