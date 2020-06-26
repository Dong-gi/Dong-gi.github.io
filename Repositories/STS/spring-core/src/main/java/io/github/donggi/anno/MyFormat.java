package io.github.donggi.anno;

@interface MyFormat {
    String prefix() default "<";
    String suffix() default ">";
}
