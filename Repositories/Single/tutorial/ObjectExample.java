class ObjectExample {
    public static void main(String[] args) throws Exception {
        var main1 = new Main();
        var main2 = main1.clone();

        Main main = new Sub(); // 업캐스팅, 부모의 참조 변수로 자손의 객체를 가리킬 수 있다.
        Main[] mains = { new Main(), new Sub() };
        Sub[] subs = { new Sub(), new Sub() };
        mains = subs; // 배열 업캐스팅 : 배열 사이에도 부모-자식 관계가 성립한다.

        for (var m : mains) {
            if (m instanceof Sub)
                System.out.println("Sub 객체 발견");
        }
    }

    private static class Sub extends Main {}

    // compareTo() 메소드를 오버라이드할 때, Comparable 인터페이스를 구체화한다.
    private static class Main implements Cloneable, Comparable<Main> {
        private int num = 0;
        private String string = "Hello World";

        @Override
        public Main clone() {
            // 얕은 복사가 필요하다면 Object.clone()을 이용한다.
            try {
                var main = (Main) super.clone();
                main.string = new String(string);
                return main;
            } catch (CloneNotSupportedException e) {
                // swallow
                return null;
            }
        }

        // equals()와 hashCode()를 함께 override해야한다.
        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if ((o instanceof Main) == false)
                return false;
            var main = (Main) o;
            return main.num == num && main.string.equals(string);
        }

        // hashCode() : 객체를 식별할 수 있는 32비트 정수를 반환.
        // 수정되지 않은 객체의 해시 코드는 일정해야 한다.
        // equals() 결과가 true면 해시 코드도 같아야 하고, false면 달라야 한다.
        @Override
        public int hashCode() {
            return num ^ string.hashCode();
        }

        @Override
        public String toString() {
            // default : 클래스 이름 + @ + 해시 코드
            return "num = " + num + ", string" + string;
        }

        @Override
        public int compareTo(Main main) {
            return (num + string).compareTo(main.num + main.string);
        }
    }
}
