package io;

import java.io.File;

public class FileFilterExample {

    public static void main(String[] args) {
        var root = new File("C:\\");

        System.out.println("전체 목록");
        for (var file : root.listFiles()) {
            System.out.print(file.getName() + ", ");
        }

        System.out.println("\n\n공백이 있는 목록");
        for (var file : root.listFiles((file) -> file.getName().contains(" "))) {
            System.out.print(file.getName() + ", ");
        }
    }

}