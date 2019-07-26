public class Test {
    static {
        System.loadLibrary("NativeCode");
    }

    int i;
    public native void test();
    
    public static void main(String[] args) {
        Test test = new Test();
        
        test.i = 10;
        System.out.println(test.i);
        
        test.test();
        System.out.println(test.i);
    }
}