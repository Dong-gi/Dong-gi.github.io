import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class Updates {

	public static void main(String[] args) throws Exception {
		var url = new URL("http://www.oracle.com/"); 
		var conn = url.openConnection(); 
		var reader = new BufferedReader(
		    new InputStreamReader(conn.getInputStream()));
	}

}
