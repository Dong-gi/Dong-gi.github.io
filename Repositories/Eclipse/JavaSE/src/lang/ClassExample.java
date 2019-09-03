package lang;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;

public class ClassExample<T extends Object, V> implements Serializable {

    private static final class Test extends ClassExample<Integer, Integer> {}

    public static void main(String[] args) throws Exception {
        System.out.println(ClassExample.class);                     // class lang.ClassExample
        System.out.println(void.class);                             // void
        System.out.println(java.lang.Deprecated.class);             // interface java.lang.Deprecated
        System.out.println(ClassExample.class.toGenericString());   // public class lang.ClassExample<T,V>
        System.out.println(new int[2].getClass().getName());        // [I
        System.out.println(new int[2].getClass().toGenericString());// int[]

        System.out.println(Test.class);                         // class lang.ClassExample$Test
        System.out.println(Test.class.getName());               // lang.ClassExample$Test
        System.out.println(new Test().getClass().getName());    // lang.ClassExample$Test
        System.out.println(Test[].class.getName());             // [Llang.ClassExample$Test;
        System.out.println(Test.class.getCanonicalName());      // lang.ClassExample.Test
        System.out.println(Test.class.toGenericString());       // private static final class lang.ClassExample$Test

        var th = Thread.class.getDeclaredConstructor(Runnable.class).newInstance(new Runnable() {
            @Override
            public void run() {
                System.out.println("I'm here : " + this.getClass()); // I'm here : class lang.ClassExample$1
                System.out.println(Runnable.class.isInstance(this)); // true
            }
        });
        th.start();
        th.join();

        System.out.println(int.class.isInstance(0));        // false
        var arr = new int[3];
        System.out.println(int[].class.isInstance(arr));    // true
        System.out.println(int[][].class.isInstance(arr));  // false

        System.out.println(Object.class.isAssignableFrom(Integer.class)); // true
        System.out.println(Integer.class.isAssignableFrom(Object.class)); // false

        System.out.println(int.class.equals(Integer.TYPE)); // true
        System.out.println(int.class.equals(Integer.class));// false
        System.out.println(Integer.TYPE);   // int
        System.out.println(Integer.class);  // class java.lang.Integer

        Class<?> c = new ArrayList<String>().getClass();
        System.out.println(c.getSuperclass());          // class java.util.AbstractList
        System.out.println(c.getGenericSuperclass());   // java.util.AbstractList<E>
        
        c = Test.class;
        System.out.println(c.getSuperclass());          // class lang.ClassExample
        System.out.println(c.getGenericSuperclass());   // lang.ClassExample<java.lang.Integer, java.lang.Integer>
        System.out.println(c.getDeclaringClass());      // class lang.ClassExample
        System.out.println(c.getEnclosingClass());      // class lang.ClassExample
        
        c = InterruptedException.class;
        System.out.println(c.getSuperclass());          // class java.lang.Exception
        System.out.println(c.getGenericSuperclass());   // class java.lang.Exception

    }

}
