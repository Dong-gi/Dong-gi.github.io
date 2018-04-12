import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Updates {

	public static void main(String[] args) throws Exception {
		var arr = new int[] {1, 2, 3, 4};
		for(var n : arr) {
			for(var i = 0; i < n; ++i) {
				System.out.print(i + " ");
			}
		}

		try(var out = new ByteArrayOutputStream() {
			@Override
			public void close() throws IOException {
				super.close();
				System.out.println("Closed!!");
			}
		}) {
			out.write("Hello".getBytes());
		}
	}

}
