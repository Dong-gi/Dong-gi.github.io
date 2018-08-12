package io;

import java.io.File;
import java.io.FilePermission;
import java.io.FileWriter;

public class FilePermissionExample {
    private static FilePermission permission = new FilePermission("*", "read");
    
    public static boolean check(FilePermission p) {
        return permission.implies(p);
    }
    public static void main(String[] args) {
        try {
            var file = new File("test.txt");
            if(check(new FilePermission(file.getCanonicalPath(), "write"))) {
                new FileWriter(file).append("data").close();
            } else {
                System.out.println("no write permission");
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}