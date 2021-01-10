import java.util.*;

class Generic2 {
	// Generic Constructor
	public <T> Generic2(T arg) {
	}

	// 비제네릭 클래스 - 제네릭 클래스 사이의 상속 가능
	class Generic3<E> extends Generic2 {
		public Generic3(E arg) {
			super(arg);
		}
	}

	public static void main(String[] args) {
		var l1 = new ArrayList<String>();
		var l2 = new ArrayList<Integer>();
		l2.add(1);

		System.out.println(isEmpty(l1));
		System.out.println(isEmpty(l2));
		// System.out.println(hasNumber(l1));
		System.out.println(hasNumber(l2));
		System.out.println(hasNumber2(l2));
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