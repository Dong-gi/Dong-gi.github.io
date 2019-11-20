package io.github.donggi;

public class HangulUtility {

    public static void main(String[] args) {
        System.out.println(new HangulStringBuilder("때리지 말고 말").roUro().append("해"));
        // 때리지 말고 말로 해

        System.out.println(
                new HangulStringBuilder()
                .append("‘-이/-가’와 ‘-을/-를’").unNun()
                .append("앞말").iGa()
                .append("다른 말에 대하여 갖는 일정한 자격").ulRul()
                .append("나타내는 조사인 ‘격조사’이고, ‘-은/-는’").unNun()
                .append("어떤 특별한 뜻").ulRul()
                .append("더해 주는 조사인 보조사라는 점").ulRul()
                .append("고려할 필요").iGa()
                .append("있습니다. 주격 조사 ‘-이/-가’").unNun()
                .append("‘산").iGa()
                .append("높다/바다").iGa()
                .append("깊다’").waGwa()
                .append("같이, 그 앞말").ulRul()
                .append("문장에서 주어").iGa()
                .append("되게 하는 일").ulRul()
                .append("하고, 목적격 조사 ‘-을/-를’").unNun()
                .append("‘책").ulRul()
                .append("보다/사과").ulRul()
                .append("먹다’와 같이, 그 앞말").ulRul()
                .append("문장에서 목적어").iGa()
                .append("되게 하는 일").ulRul()
                .append("하며, 보조사 ‘-은/-는’").unNun()
                .append("‘인생").unNun()
                .append("짧고, 예술").unNun()
                .append("길다.’").waGwa()
                .append("같이 어떤 대상").iGa()
                .append("다른 것").waGwa()
                .append("대조됨").ulRul()
                .append("나타냅니다.")
                );
        // ‘-이/-가’와 ‘-을/-를’은 앞말이 다른 말에 대하여 갖는 일정한 자격을 나타내는 조사인 ‘격조사’이고, ‘-은/-는’은 어떤 특별한 뜻을 더해 주는 조사인 보조사라는 점을 고려할 필요가 있습니다. 주격 조사 ‘-이/-가’는 ‘산이 높다/바다가 깊다’와 같이, 그 앞말을 문장에서 주어가 되게 하는 일을 하고, 목적격 조사 ‘-을/-를’은 ‘책을 보다/사과를 먹다’와 같이, 그 앞말을 문장에서 목적어가 되게 하는 일을 하며, 보조사 ‘-은/-는’은 ‘인생은 짧고, 예술은 길다.’와 같이 어떤 대상이 다른 것과 대조됨을 나타냅니다.
    }

    /**
     * 코드 포인트 = (초성 인덱스 * 21 + 중성 인덱스) * 28 + 종성 인덱스 + '가'0xAC00<br>
     * (코드 포인트 - '가'0xAC00) % 28 = 종성 인덱스<br>
     * @return 종성 인덱스
     */
    public static int jongseongIndex(char letter) {
        if(letter < '가' || letter > '힣')
            throw new IllegalArgumentException(String.format("The letter '%c' is not a Hangul", letter));
        return (letter - '가') % 28;
    }

    /**
     * (코드 포인트 - 종성 인덱스 - '가'0xAC00) / 28 % 21 = 중성 인덱스<br>
     * @return 중성 인덱스
     */
    public static int jungseongIndex(char letter) {
        if(letter < '가' || letter > '힣')
            throw new IllegalArgumentException(String.format("The letter '%c' is not a Hangul", letter));
        return (letter - jongseongIndex(letter) - '가') / 28 % 21;
    }

    /**
     * ((코드 포인트 - 종성 인덱스 - '가'0xAC00) / 28 - 중성 인덱스) / 21 = 초성 인덱스
     * @return 초성 인덱스
     */
    public static int choseongIndex(char letter) {
        if(letter < '가' || letter > '힣')
            throw new IllegalArgumentException(String.format("The letter '%c' is not a Hangul", letter));
        return ((letter - jongseongIndex(letter) - '가') / 28 - jungseongIndex(letter)) / 21;
    }

    /**
     * 문자열의 마지막 한글 문자를 반환한다
     */
    public static char lastHangul(CharSequence csq) {
        for(var i = csq.length() - 1; i >= 0; --i) {
            final var letter = csq.charAt(i);
            if(letter < '가' || letter > '힣')
                continue;
            return letter;
        }
        throw new IllegalArgumentException(String.format("\"%s\" doesn't contains a Hangul", csq));
    }

    /**
     * 문자열 마지막 종성 문자의 종성 인덱스를 반환한다. 종성을 가진 글자를 발견하지 못하면 -1을 반환한다.
     */
    public static int lastJongseongIndex(CharSequence csq) {
        for(var i = csq.length() - 1; i >= 0; --i) {
            final var letter = csq.charAt(i);
            if(letter < '가' || letter > '힣')
                continue;
            final var jongseongIndex = jongseongIndex(letter);
            if(jongseongIndex == 0)
                continue;
            return jongseongIndex;
        }
        return -1;
    }

    /** 
     * 문자가 종성을 갖는지 여부를 반환
     */
    public static boolean hasJongseong(char letter) {
        try {
            return jongseongIndex(letter) != 0;
        } catch(Exception e) {
            return false;
        }
    }

    public static class HangulStringBuilder {

        private StringBuilder builder;

        public HangulStringBuilder() {
            builder = new StringBuilder();
        }
        public HangulStringBuilder(CharSequence charSequence) {
            builder = new StringBuilder(charSequence);
        }

        public HangulStringBuilder append(CharSequence charSequence) {
            builder.append(charSequence);
            return this;
        }

        public HangulStringBuilder select(CharSequence whenJongseong, CharSequence notJongseong) {
            for(var idx = builder.length() - 1; idx >= 0; --idx)
                try {
                    builder.append(hasJongseong(builder.charAt(idx))? whenJongseong : notJongseong);
                    break;
                } catch(Exception e) {}
            return this;
        }

        public HangulStringBuilder unNun() {
            return select("은 ", "는 ");
        }

        public HangulStringBuilder iGa() {
            return select("이 ", "가 ");
        }

        public HangulStringBuilder ulRul() {
            return select("을 ", "를 ");
        }

        public HangulStringBuilder waGwa() {
            return select("과 ", "와 ");
        }

        public HangulStringBuilder roUro() {
            if(lastJongseongIndex(builder) == 8)
                return append("로 ");
            return select("으로 ", "로 ");
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
