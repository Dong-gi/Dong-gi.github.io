import java.io.Externalizable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

public class ExternalizableExample {

	public static class Student implements Externalizable {
		private String name;
		private int id;
		
		public Student() {}
		
		public Student(String name, int id) {
			this.name = name;
			this.id = id;
		}
		
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			name = in.readUTF();
			id = in.readInt();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(name);
			out.writeInt(id);
		}
		
		@Override
		public String toString() {
			return "name: " + name + ", id: " + id;
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		Student student = new Student("name1", 2014136011);
		
		ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("student.data"));
		out.writeObject(student);
		out.close();
		
		ObjectInputStream in = new ObjectInputStream(new FileInputStream("student.data"));
		student = (Student) in.readObject();
		in.close();
		
		System.out.println(student.toString());
	}

}
