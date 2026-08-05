import lombok.ToString;

@ToString(callSuper=true)
public class LombokToStringExample {
    private String[] alphabet = new String[] {"a", "b", "c", "d"};

    public static void main(String[] args) {
        System.out.println(new LombokToStringExample());
        // LombokToStringExample(super=LombokToStringExample@5305068a, alphabet=[a, b, c, d])
    }

}