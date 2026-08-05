class LabelFor {
	public static void main(String[] args) {
		outer: for (;;)
			middle: while (true)
				inner: for (;;)
					break outer;
		System.out.println("중첩 반복문 종료");
	}
}