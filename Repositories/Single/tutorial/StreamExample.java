import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class StreamExample {
	public static void main(String[] args) {
		System.out.println(new Random().ints(10).mapToObj(String::valueOf).collect(Collectors.joining(", ")));
        // 1501849784, 82283898, 1109413392, -804828574, 1925770194, 1944556750, 1138261689, -1718640127, -1501041825, -2087591008
		System.out.println(Arrays.toString(Stream.iterate(0, i -> i + 2).limit(20).toArray()));
        // [0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38]
	}
}