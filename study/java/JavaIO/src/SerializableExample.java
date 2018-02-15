import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerializableExample {

	public static class Student implements Serializable {
		private static final long serialVersionUID = 1L; // == default

		private String name;
		private int id;

		public Student() {}

		public Student(String name, int id) {
			this.name = name;
			this.id = id;
		}

		@Override
		public String toString() {
			return "name: " + name + ", id: " + id;
		}

	}

	public static void main(String[] args) throws Exception {
		Student student = new Student("name1", 2014136011);

		try(ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("student.data"));
				ObjectInputStream in = new ObjectInputStream(new FileInputStream("student.data"));) {
			out.writeObject(student);
			student = (Student) in.readObject();
			System.out.println(student.toString());
		}
	}

}
