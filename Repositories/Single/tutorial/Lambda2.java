class Lambda2 {
    @FunctionalInterface
    interface Joiner{
        String join(String s1, String s2);
    }

    @FunctionalInterface
    interface Adder{
        int add(int a, int b);
    }

    public static void test(Joiner joiner){
        String s1 = "Hello";
        String s2 = "World";
        System.out.println(joiner.join(s1, s2));
    }

    public static void test(Adder adder){
        int n1 = 10;
        int n2 = 5;
        System.out.println(adder.add(n1, n2));
    }

    public static void main(String[] args) {
        test((String x, String y) -> x + "," + y);
        test((Joiner) ((x, y) -> x + " " + y));

        Adder adder = (x,y) -> x + y;
        test(adder);
    }
}