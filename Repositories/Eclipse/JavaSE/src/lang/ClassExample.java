package lang;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;

public class ClassExample<T extends Object, V> implements Serializable {

    private static final class Test extends ClassExample<Integer, Integer> {}

    public static void main(String[] args) {
        System.out.println(ClassExample.class);
        System.out.println(void.class);
        System.out.println(java.lang.Deprecated.class);
        System.out.println(ClassExample.class.toGenericString());
        System.out.println(new int[2].getClass().getName());
        System.out.println(new int[2].getClass().toGenericString() + "\n");

        System.out.println(Test.class);
        System.out.println(Test.class.getName());
        System.out.println(new Test().getClass().getName());
        System.out.println(Test[].class.getName());
        System.out.println(Test.class.getCanonicalName());
        System.out.println(Test.class.toGenericString() + "\n");

        try {
            var th = Thread.class.getDeclaredConstructor(Runnable.class).newInstance(new Runnable() {
                @Override
                public void run() {
                    System.out.println("I'm here : " + this.getClass() + "\n");
                    System.out.println(Runnable.class.isInstance(this));
                }
            });
            th.start();
            th.join();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException | InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(int.class.isInstance(0));
        var arr = new int[3];
        System.out.println(int[].class.isInstance(arr));
        System.out.println(int[][].class.isInstance(arr) + "\n");

        System.out.println(Object.class.isAssignableFrom(Integer.class));
        System.out.println(Integer.class.isAssignableFrom(Object.class) + "\n");

        System.out.println(int.class.equals(Integer.TYPE));
        System.out.println(int.class.equals(Integer.class));
        System.out.println(Integer.TYPE);
        System.out.println(Integer.class + "\n");

        Class<?> c = new ArrayList<String>().getClass();
        System.out.println(c.getSuperclass());
        System.out.println(c.getGenericSuperclass());
        c = Test.class;
        System.out.println(c.getSuperclass());
        System.out.println(c.getGenericSuperclass());
        System.out.println(c.getDeclaringClass());
        System.out.println(c.getEnclosingClass());
        c = InterruptedException.class;
        System.out.println(c.getSuperclass());
        System.out.println(c.getGenericSuperclass());

    }

}
