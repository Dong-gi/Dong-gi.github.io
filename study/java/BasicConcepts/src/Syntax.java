
public class Syntax {

	public static void main(String[] args) {
		int var1 = 0;
		switch(var1)
		{
		case 0:
			System.out.println("Zero");
			break;
		default:
			System.out.println("Non Zero");
		}

		char var2 = '0';
		switch(var2)
		{
		case '0':
			System.out.println("Zero Character");
			break;
		case 0:
			System.out.println("Zero Number");
			break;
		case '\uFFFF':
			System.out.println("What's This?");
			break;
		}

		String var3 = "asdf";
		switch(var3)
		{
		case "0":
			System.out.println("Zero String");
			break;
		default:
			System.out.println("What's This? : " + var3);
		}

		outer:
			for( ; ; ) {
				inner:
					for( ; ; ) {
						break outer;
					}
			}
		System.out.println("이중 반복문 종료");

		int[] arr = new int[] {1, 2, 3, 4};
		for(int i : arr) {
			System.out.print(" " + i);
		}
	}

}
