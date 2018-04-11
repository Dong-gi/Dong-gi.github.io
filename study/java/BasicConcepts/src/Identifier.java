
public class Identifier {

	private class MyPrivateClass {}
	
	private static final int MY_PRIVATE_NUMBER = 7;
	
	private void myPrivateMethod() {}
	
	public static void main(String[] args) {
		var myNumber = 7;
		
		System.out.println();
		// System : 대문자로 시작하므로 클래스라고 추측.
		// . : 내부 네임스페이스 접근
		// out : 소문자로 시작하므로 객체, 변수 등으로 추측.
		// println() : 소문자로 시작하고 소괄호가 존재하므로 메서드로 추측.
		
		// 이클립스와 같은 IDE를 사용한다면 out의 타입이 'public static final PrintStream'임을 확인할 수 있다.
		// 싱글톤, 상수지만 타이핑 용이를 위해 소문자로만 구성
	}

}
