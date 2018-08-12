package io;

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
        var student = new Student("김동기", 2014136011);

        try(var out = new ObjectOutputStream(new FileOutputStream("student.data"));
                var in = new ObjectInputStream(new FileInputStream("student.data"));) {
            out.writeObject(student);
            student = (Student) in.readObject();
            System.out.println(student.toString());
        }
    }

}
