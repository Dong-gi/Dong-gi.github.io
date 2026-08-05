import lombok.EqualsAndHashCode;

public class LombokEqualsAndHashCodeExample {

    @EqualsAndHashCode
    static class A {
        int a = 0;
    }

    @EqualsAndHashCode(callSuper=false)
    static class B extends A {
        int b = 0;
    }

    @EqualsAndHashCode(callSuper=true)
    static class C extends A {
        int c = 0;
    }

    public static void main(String[] args) {
        System.out.println(new A().hashCode()); // 59
        System.out.println(new B().hashCode()); // 59
        System.out.println(new C().hashCode()); // 3481
    }

}