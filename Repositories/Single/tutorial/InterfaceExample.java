class InterfaceExample implements Flyable {
	public static void main(String[] args) {
		var w = new Flyable.Wing();
		new InterfaceExample().fly();
		new InterfaceExample().flyFast();
		System.out.println(Flyable.numberOfWings());
		System.out.println(Flyable2.numberOfWings());
	}

	@Override
	public void fly() {
		System.out.println("날기 싫어");
	}
}

interface Flyable {
	class Wing {
	}

	Wing LEFT_WING = new Wing();
	Wing RIGHT_WING = new Wing();

	void fly();

	default void flyFast() {
		System.out.println("빠르게 날기");
	}

	static int numberOfWings() {
		return 2;
	}
}

interface Flyable2 extends Flyable {
	Flyable.Wing BACK_WING = new Flyable.Wing();

	static int numberOfWings() {
		return 3;
	}
}