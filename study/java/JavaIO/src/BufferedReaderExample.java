import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class BufferedReaderExample {

	public static void main(String[] args) throws Exception {
		var urlConnection = new URL("https://www.naver.com").openConnection();
        var reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));
        
        System.out.println("All anchor tag lines>>");
        for(var line : reader.lines().filter((String line) -> line.contains("<a")).toArray()) {
        	System.out.println(line.toString().trim());
        }
	}

}
