import java.util.List;

import lombok.Builder;
import lombok.Singular;
import lombok.ToString;

/**
 * {@link lombok.Builder} 예제
 * 
 * <p>
 * </p>
 */
public class LombokBuilderExample {

    @Builder(builderClassName = "MsgBuilder")
    static String msg(String prefix, String suffix) {
        return prefix + " message " + suffix;
    }

    @Builder
    @ToString
    static class Message {
        String msg;
    }

    @ToString
    static class Message2 {
        String msg;

        private Message2() {}

        @Builder
        public static Message2 of(String msg) {
            var obj = new Message2();
            obj.msg = ">> " + msg;
            return obj;
        }
    }

    @ToString
    static class Message3 {
        String current;
        String before;

        @Builder(toBuilder = true)
        public Message3 update(
                @Builder.ObtainVia(method = "updateCurrentMessage") String current,
                @Builder.ObtainVia(field = "current") String before) {
            this.current = current;
            this.before = before;
            return this;
        }

        private static String updateCurrentMessage() {
            return System.nanoTime() + "";
        }
    }

    @Builder
    @ToString
    static class Message4 {
        @Builder.Default String message = System.nanoTime() + "";
    }

    @Builder
    @ToString
    static class Message5 {
        @Singular List<String> messages;
    }

    public static void main(String[] args) {
        System.out.println(new MsgBuilder().prefix(">>").suffix("<<").build()); // >> message <<

        System.out.println(Message.builder().build()); // LombokBuilderExample.Message(msg=null)
        System.out.println(Message.builder().msg("Hello World").build()); // LombokBuilderExample.Message(msg=Hello World)
        System.out.println(Message2.builder().msg("Hello World").build()); // LombokBuilderExample.Message2(msg=>> Hello World)

        var msg3 = new Message3();
        System.out.println(msg3); // LombokBuilderExample.Message3(current=null, before=null)
        msg3 = msg3.toBuilder().build();
        System.out.println(msg3); // LombokBuilderExample.Message3(current=166343171685900, before=null)
        msg3 = msg3.toBuilder().build();
        System.out.println(msg3); // LombokBuilderExample.Message3(current=166343171783200, before=166343171685900)

        System.out.println(Message4.builder().build()); // LombokBuilderExample.Message4(message=166449864120100)
        System.out.println(Message5.builder().message("Hello").message("World").build()); // LombokBuilderExample.Message5(messages=[Hello, World])
    }

}