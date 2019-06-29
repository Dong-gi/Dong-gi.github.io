package io.github.donggi;

public class HangulUtility {

    public static void main(String[] args) {
        System.out.println(
                new HangulStringBuilder()
                    .append("‘-이/-가’와 ‘-을/-를’").unNun()
                    .append("앞말").iGa()
                    .append("다른 말에 대하여 갖는 일정한 자격").ulRul()
                    .append("나타내는 조사인 ‘격조사’이고, ‘-은/-는’").unNun()
                    .append("어떤 특별한 뜻").ulRul()
                    .append("더해 주는 조사인 보조사라는 점").ulRul()
                    .append("고려할 필요").iGa()
                    .append("있습니다.\n주격 조사 ‘-이/-가’").unNun()
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
                    .append("하며,\n보조사 ‘-은/-는’").unNun()
                    .append("‘인생").unNun()
                    .append("짧고, 예술").unNun()
                    .append("길다.’").waGwa()
                    .append("같이 어떤 대상").iGa()
                    .append("다른 것").waGwa()
                    .append("대조됨").ulRul()
                    .append("나타냅니다.")
                );
        /* ‘-이/-가’와 ‘-을/-를’은 앞말이 다른 말에 대하여 갖는 일정한 자격을 나타내는 조사인 ‘격조사’이고, ‘-은/-는’은 어떤 특별한 뜻을 더해 주는 조사인 보조사라는 점을 고려할 필요가 있습니다.
         * 주격 조사 ‘-이/-가’는 ‘산이 높다/바다가 깊다’와 같이, 그 앞말을 문장에서 주어가 되게 하는 일을 하고, 목적격 조사 ‘-을/-를’은 ‘책을 보다/사과를 먹다’와 같이, 그 앞말을 문장에서 목적어가 되게 하는 일을 하며,
         * 보조사 ‘-은/-는’은 ‘인생은 짧고, 예술은 길다.’와 같이 어떤 대상이 다른 것과 대조됨을 나타냅니다.
         */
    }

    /**
     * 코드 포인트 = (초성 인덱스 * 21 + 중성 인덱스) * 28 + 종성 인덱스 + 0xAC00<br>
     * (코드 포인트 - 0xAC00) % 28 = 종성 인덱스<br>
     * (코드 포인트 - 0xAC00) / 28 % 21 = 중성 인덱스<br>
     * (코드 포인트 - 0xAC00 - 종성 인덱스) / 28 - 중성 인덱스 = 초성 인덱스
     * @return 종성을 갖는지 여부를 반환
     */
    public static boolean hasJongseong(char letter) {
        if(letter < '가' || letter > '힣')
            throw new IllegalArgumentException(String.format("The letter '%c' is not a Hangul", letter));
        return (letter - '가') % 28 != 0;
    }
    
    public static class HangulStringBuilder  {

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

        public HangulStringBuilder unNun() {
            for(var idx = builder.length() - 1; idx >= 0; --idx)
                try {
                    builder.append(hasJongseong(builder.charAt(idx))? '은' : '는');
                    builder.append(' ');
                    break;
                } catch(Exception e) {}
            return this;
        }

        public HangulStringBuilder iGa() {
            for(var idx = builder.length() - 1; idx >= 0; --idx)
                try {
                    builder.append(hasJongseong(builder.charAt(idx))? '이' : '가');
                    builder.append(' ');
                    break;
                } catch(Exception e) {}
            return this;
        }

        public HangulStringBuilder ulRul() {
            for(var idx = builder.length() - 1; idx >= 0; --idx)
                try {
                    builder.append(hasJongseong(builder.charAt(idx))? '을' : '를');
                    builder.append(' ');
                    break;
                } catch(Exception e) {}
            return this;
        }

        public HangulStringBuilder waGwa() {
            for(var idx = builder.length() - 1; idx >= 0; --idx)
                try {
                    builder.append(hasJongseong(builder.charAt(idx))? '과' : '와');
                    builder.append(' ');
                    break;
                } catch(Exception e) {}
            return this;
        }

        public HangulStringBuilder roUro() {
            for(var idx = builder.length() - 1; idx >= 0; --idx)
                try {
                	if(hasJongseong(builder.charAt(idx)))
                		builder.append('으');
                    builder.append('로');
                    builder.append(' ');
                    break;
                } catch(Exception e) {}
            return this;
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
