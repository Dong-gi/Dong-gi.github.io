import java.io.FileInputStream;
import java.io.FileOutputStream;

public class InputStreamExample {

	public static void main(String[] args) throws Exception {
		try(var out = new FileOutputStream("string.data");) {
			out.write("Hello World".getBytes());
		}

		try(var in = new FileInputStream("string.data");
				var out = new FileOutputStream("string2.data");) {
			System.out.println(in.available());
			in.transferTo(out);
		}

		try(var in = new FileInputStream("string2.data");) {
			System.out.println(new String(in.readAllBytes()));
		}
	}

}
