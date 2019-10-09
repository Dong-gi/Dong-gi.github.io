import lombok.val;

public class LombokValExample {
    
    public static void main(String[] args) {
        val name = "Hello";
        // name = "World"; // The final local variable name cannot be assigned
        System.out.println(name.getClass()); // class java.lang.String
    }
    
}