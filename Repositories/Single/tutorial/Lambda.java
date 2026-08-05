class Lambda {
	@FunctionalInterface
	public static interface DoSomething1 {
		void doSomething1();
	}

	@FunctionalInterface
	public static interface DoSomething2 {
		static DoSomething2 getInstance() {
			return new DoSomething2() {
				@Override
				public DoSomething2 doSomething1() {
					System.out.println("픽터리를 인터페이스 내에서 구현 가능");
					return this;
				}
			};
		}

		Object doSomething1();

		int NUM = 1; // final 변수도 가질 수 있다.

		private void doSomething2() {
			System.out.println("private 메서드도 가질 수 있다. 밖에서는 못부르징~");
		}

		public default void doSomething3() {
			doSomething2();
		}
	}

	private Lambda() {
		System.out.println("시그너쳐만 맞으면 다 보낼 수 있다.");
	}

	public static void main(String[] args) {
		doSomething1(new DoSomething1() {
			@Override
			public void doSomething1() {
				System.out.println("옛날 방식");
			}
		});
		doSomething1(() -> System.out.println("최근 방식"));

		doSomething2(DoSomething2.getInstance());
		doSomething1(DoSomething2.getInstance()::doSomething3);
		doSomething1(Lambda::new); // 생성자를 void 리턴에도 보낼 수 있군
		// doSomething(Lambda::main); 이건 안 되지
	}

	public static void doSomething1(DoSomething1 doSomething) {
		doSomething.doSomething1();
	}

	public static void doSomething2(DoSomething2 doSomething) {
		var obj = doSomething.doSomething1();
		if (obj instanceof Lambda) {
			Lambda.main(null);
		}
	}
}