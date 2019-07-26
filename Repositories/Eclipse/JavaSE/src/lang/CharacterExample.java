package lang;


public class CharacterExample {

    public static void main(String[] args) {
        System.out.println(">> offsetByCodePoints");
        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 0, 2));
        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 1, 2));
        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 2, 2));
        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 3, 2));
        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 4, 2) + "\n");

        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 7, -2));
        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 6, -2));
        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 5, -2));
        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 4, -2));
        System.out.println(Character.offsetByCodePoints("a🀂🀂🀂🀂z", 3, -2) + "\n");

        System.out.println(Character.isUnicodeIdentifierPart(Character.codePointAt("가", 0)));
        System.out.println(Character.isUnicodeIdentifierPart(Character.codePointAt("🀂", 0)) + "\n");

        System.out.println(Character.MIN_RADIX + ", " + Character.MAX_RADIX);
        System.out.println(Character.digit('A', 2));
        System.out.println(Character.digit('A', 8));
        System.out.println(Character.digit('A', 16));
        System.out.println(Character.digit('z', 32));
        System.out.println(Character.digit('z', 35));
        System.out.println(Character.digit('z', 36) + "\n");

        System.out.println("non-breaking space : " + '\u00A0' + "," + '\u2007' + "," + '\u202F');
        System.out.println(Character.isWhitespace('\u2007'));

        System.out.println(Character.isMirrored('('));
    }

}
