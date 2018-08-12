package io.github.donggi;

import java.util.ArrayList;

public class AnonymousClass {

    static interface I {}
    
    public static void main(String[] args) {
        var class2 = new AnonymousClass() {
            int newField;
            void newMethod() {}
        };
        
        var i = new I() {
            int newField;
            void newMethod() {}
        };
        
        var list1 = new ArrayList<>() {{ add(1); add(2); }};
        var list2 = new ArrayList<Integer>() {
            { add(1); add(2); }
            @Override
            public boolean add(Integer i) {
                System.out.println("You added " + i);
                return super.add(i);
            }
        };
    }

}