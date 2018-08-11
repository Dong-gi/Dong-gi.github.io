package io.github.donggi;

public class Lambda {

	@FunctionalInterface
	public interface DoSomething {
		void doSomething();
	}
	@FunctionalInterface
	public interface DoSomething2 {
		static DoSomething2 getInstance() {
			return new DoSomething2() {
				@Override
				public DoSomething2 doSomething() {
					System.out.println("픽터리를 별개의 클래스로 만들지 않아도 됩니다.");
					return this;
				}
			};
		}
		Object doSomething();
		
		int NUM=1; // final 변수도 가질 수 있다.
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
		doSomething(new DoSomething() {

			@Override
			public void doSomething() {
				System.out.println("옛날 방식");
			}

		});

		doSomething(() -> {
			System.out.println("최근 방식");
		});

		doSomething2(DoSomething2.getInstance());
		doSomething(DoSomething2.getInstance()::doSomething3);
		doSomething(Lambda::new); // 생성자를 void 리턴에도 보낼 수 있군
		// doSomething(Lambda::main); 이건 안 되지
		doSomething2(Lambda::new); // 핫하, 이건 몰랐지?
	}

	public static void doSomething(DoSomething doSomething) {
		doSomething.doSomething();
	}
	public static void doSomething2(DoSomething2 doSomething) {
		Object obj = doSomething.doSomething();
		if(obj instanceof Lambda) {
			Lambda.main(null);
		}
	}
}