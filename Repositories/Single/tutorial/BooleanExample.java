public class BooleanExample {
    public static void main(String[] args) {
        boolean flag;
        flag = true; // true literal
        flag = false; // false literal

        System.out.println(sayFalse() & sayTrue());
        System.out.println(sayFalse() && sayTrue()); // Short-circuit

        System.out.println(sayTrue() | sayFalse());
        System.out.println(sayTrue() || sayFalse()); // Short-circuit
    }

    public static boolean sayFalse() {
        System.out.print("거짓말입니다. ");
        return false;
    }

    public static boolean sayTrue() {
        System.out.print("참말입니다. ");
        return true;
    }
}