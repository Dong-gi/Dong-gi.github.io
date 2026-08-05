class ClassExample {
	public static void main(String[] args) {
		Main m = new Sub();
		System.out.println(m.getNum2()); // 2
		m.setNum2(3);
		System.out.println(m.getNum2()); // 4
	}
}

class Main {
	private static int num1;
	private static final int NUM1;

	// class initializer : 클래스 로드시 한번만 실행
	// static, static final 멤버 초기화 가능
	static {
		num1 = 0;
		NUM1 = 10;
	}

	private int num2;
	private final int NUM2;

	// instance initializer : 객체 생성시 한번만 실행
	// final 멤버 초기화 가능
	{
		num2 = num1;
		NUM2 = NUM1;
	}

	public Main(int num2) {
		setNum2(num2);
	}

	// setter
	public void setNum2(int num2) {
		this.num2 = num2;
	}

	// getter
	public int getNum2() {
		return num2;
	}
}

class Sub extends Main {
	public Sub() {
		super(1);
	}

	@Override
	public void setNum2(int num2) {
		super.setNum2(num2 + 1);
	}
}