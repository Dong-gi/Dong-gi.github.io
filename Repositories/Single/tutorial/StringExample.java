class StringExample {
    public static void main(String[] args) {
        String s = "Hello World";
        s = s + 123 + false;
        s += 1.23;
        s += 'H';
        s += "ello World";
        System.out.println(s);
    }
}