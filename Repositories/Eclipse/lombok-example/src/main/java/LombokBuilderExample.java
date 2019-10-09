import java.util.List;

import lombok.Builder;
import lombok.Singular;
import lombok.ToString;

/**
 * {@link lombok.Builder} 예제
 * 
 * <p>
 * 메서드에 @Builder가 붙으면 아래의 7가지가 생성된다.<br>
 * <ul>
 * <li>클래스 내부) 클래스명 + "Builder"를 이름으로 갖는 inner static class</li>
 * <li>클래스 내부) builder() 메서드</li>
 * <li>Builder 클래스 내부) @Builder 타깃 각각의 인자들과 같은 non-static, non-final 필드</li>
 * <li>Builder 클래스 내부) package private 기본 생성자</li>
 * <li>Builder 클래스 내부) 각 필드의 이름으로 한 setter 메서드. 반환은 Builder 객체 자체</li>
 * <li>Builder 클래스 내부) @Builder 타깃을 호출하는 build() 메서드</li>
 * <li>Builder 클래스 내부) toString() 메서드</li>
 * </ul>
 * <br>
 * 클래스에 @Builder가 붙으면, @AllArgsConstructor(access = AccessLevel.PACKAGE)를 클래스에 붙이고
 * 생성된 생성자에 @Builder를 붙이는 것과 같다.<br>
 * <br>
 * toBuilder 옵션을 이용해 기존 객체로부터 시작하는 Builder를 생성할 수 있다.<br>
 * 기타 구성 옵션 : builderClassName, buildMethodName, builderMethodName<br>
 * Builder 클래스를 사용자 정의 타입으로 정의하고 구현하는 경우, 직접 추가하지 않은 요소는 lombok이 자동 구현한다.<br>
 * <br>
 * {@code @Singular}를 이용해 Collection 원소를 하나씩 추가하여 최종적으로 변경 불가능한 Collection 객체로 초기화할 수 있다.<br>
 * 원소는 필드의 단수명을 이름으로 하는 메서드를 이용해 추가할 수 있으며, 일반적인 단수명은 자동으로 추론하지만, 그 외의 경우에는 @Singuler("axis")처럼 value로 넘겨줘야 한다.<br>
 * lombok에서 지원하는 Collection만 사용 가능하다.<br>
 * java.util : Iterable, Collection, List, Set, SortedSet, NavigableSet, Map, SortedMap, NavigableMap<br>
 * com.google.common.collect : ImmutableCollection, ImmutableList, ImmutableSet, ImmutableSortedSet, ImmutableMap, ImmutableBiMap, ImmutableSortedMap, ImmutableTable
 * </p>
 */
public class LombokBuilderExample {
/*
    @Builder
    @ToString
    static class A {
        long lastModified;
    }

    @ToString
    static class A2 {
        long lastModified;

        @Builder
        public A2(long lastModified) {
            this.lastModified = lastModified;
        }
    }

    @ToString
    static class A3 {
        long lastModified;

        @Builder
        public static A3 of(long lastModified) {
            A3 a3 = new A3();
            a3.lastModified = lastModified;
            return a3;
        }
    }

    @ToString
    static class A4 {
        long lastModified;
        long dump;

        @Builder(toBuilder = true)
        public A4 lastModified(
                @Builder.ObtainVia(method = "nanoTime") long lastModified,
                @Builder.ObtainVia(field = "lastModified") long dump) {
            this.lastModified = lastModified;
            this.dump = dump;
            return this;
        }

        private static long nanoTime() {
            return System.nanoTime();
        }
    }

    @Builder
    @ToString
    static class B {
        @Builder.Default long lastModified = System.currentTimeMillis();
    }
    
    @Builder
    @ToString
    static class C {
        @Singular List<Integer> nums;
    }

    public static void main(String[] args) {
        System.out.println(A.builder().build());
        System.out.println(A.builder().lastModified(1).build());
        System.out.println(A2.builder().lastModified(2).build());
        System.out.println(A3.builder().lastModified(3).build());
        
        A4 a4 = new A4();
        System.out.println(a4);
        a4 = a4.toBuilder().build();
        System.out.println(a4);
        a4 = a4.toBuilder().build();
        System.out.println(a4);
        
        System.out.println(B.builder().build());
        
        System.out.println(C.builder().num(1).num(2).num(3).build());
    }
*/
}