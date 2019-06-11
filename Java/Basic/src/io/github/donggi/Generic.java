package io.github.donggi;

import java.util.Arrays;

//Generic
interface MyInterface<E> {}

//Multi Type
interface MyInterface2<E, V> {}

//Bounded Generic, 인터페이스도 extends로 검사
class MyClass<K, V extends MyList & MyInterface<V>> {}

class MyList<E> {
    private int idx = 0;
    private E[] elements;

    @SuppressWarnings("unchecked")
    MyList() {
        elements = (E[]) new Object[0];
        // elements = new E[10]; // error

        // 단, 와일드카드를 이용한 배열 선언은 가능
        MyList<?>[] list = new MyList<?>[3];
        // MyList<E>[] list = new MyList<E>[10]; // error
        // MyList<Integer>[] list = new MyList<Integer>[10]; // error
    }

    public void add(E element) {
        try {
            elements[idx++] = element;
        } catch (Exception e) {
            elements = Arrays.copyOf(elements, elements.length + 10);
            elements[idx - 1] = element;
        }
    }

    public E get(int idx) {
        return elements[idx];
    }

    public static void wildcardAndBounded(MyList<?> l1, MyList<? super String> l2) {
    }

    // private static E e; // error
    // public static E firstElement(MyList<?> list) {} // error
    public static <T> T firstElement(MyList<T> list) { // It's OK
        return (T) list.elements[0];
    }
}

public class Generic {

    public static void main(String[] args) {
        var list = new MyList<Integer>();
        for (var i = 0; i < 20; ++i)
            list.add(i);
        System.out.println(MyList.firstElement(list));
        System.out.println(list.get(15));
    }

}
