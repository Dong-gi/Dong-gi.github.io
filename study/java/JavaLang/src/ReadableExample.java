import java.io.IOException;
import java.nio.CharBuffer;

public class ReadableExample {

	public static void main(String[] args) throws IOException {
		CharBuffer cb1 = CharBuffer.wrap("Hello World!");
		CharBuffer cb2 = CharBuffer.allocate(cb1.capacity());
		System.out.println(cb1.read(cb2));
		cb1.rewind();
		cb2.rewind();
		System.out.println("cb1 : " + cb1.toString());
		System.out.println("cb2 : " + cb2.toString());
		
	}

}
