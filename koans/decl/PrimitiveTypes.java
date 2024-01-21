package koans.decl;

public class PrimitiveTypes {


    /**
     * A variable need to have a type, and the types in java can be of three types
     *  1. Primitive type
     *  2. Array type
     *  3. Reference type
     *
     * The first primitive type is boolean
     *
     *  The type keyword is 'boolean', possibles values are 'true' and 'false'.
     *  The operators allowed are !(not),||(or),&&(and),==(equals),!=(not equals)
     *   and =(assignment).
     *   The '||' and '&&' do short circuited evaluation.
     *  Beside these boolean values are used as conditonals in ?: ( ternay op),
     *  and control flow ( for, do, while, if )
     */
    public static void booleans() {

        // The type for a boolean is 'boolean'
        // The only allowed values are 'true' and 'false'
        boolean hasWings = true;
        boolean hasLegs = false;

        // !,  ||, and && works as expected
        assert hasWings && !hasLegs == false : " TODO";
        assert !hasWings || hasLegs == true :" TODO ";

    }


    /**
     * Let's get 'char' out of out way, before we talk about integeral types.
     *
     * It's defined as  16 bit unsigned integeral type which can take
     * values from 0 ( '\u0000') to  65535 ('\UFFFF').
     *
     * But do remember that unicode is complicated, and java doesn't
     * do a good job of it. The 'char' is a 'UTF-16 code unit' rather
     * then a 'unicode character' or a 'unicode codepoint'.
     *
     * character literals will evaluate to their unicode values, when
     * they belong to basic plane ( < 0xFFFF ), and be garbage otherwise.
     *
     * Don't try to use supplementary code planes for type 'char'
     * or for character literals.
     *
     *
     */
    public static void chars() {
        char a = 'A';
        assert a + 2 == 'B' : "How much do 'A' and 'B' differ";

        char smiley = '☺';
        assert smiley - '☹' == 0  :  "These smileys differ by one only";

        // Unicode Character “🂱” (U+1F0B1) is Ace of Hearts card.
        char aceOfHearts = '🂱';
        // But instead we get '\ud83c', which is not a printable character
        assert aceOfHearts != '\ud83c' : "Avoid using supplementry code planes";

    }


    /**
     * The next primitive types are the integral, and java has quite a few
     * of them of different ranges
     *  1. byte takes 8 bits, and is signed.
     *  2. short takes 16 bits, and is signed
     *  3. int takes 32 bits, and is signed
     *  4. long takes 64 bits, and is signed.
     *
     * These are all the standard feature that are found in any language.
     * Compiler would not let you do a lossy conversion, e.g.
     * assigning an short type variable to an integer value.
     *
     * However compiler doesn't warns against overflows.
     */
    public static void integerals () {

        int maxInt = Integer.MAX_VALUE;

        assert maxInt + 1 != Integer.MIN_VALUE : "silent overflow, use with care";

        assert Long.MAX_VALUE + 1 != Long.MIN_VALUE : "silent overflow, use with care";



    }

    /**
     * Java has two floating point types,
     *   float, which is  4 byte,
     *   double, which is 8 byte.
     *
     * These are all the standard feature that are found in any language.
     * Java also support +Infinity, -Infinity and NaN
     *
     *
     */
    public static void floatings () {


        double one = 1.0;
        double zero = 0.0;

        assert Double.isNaN (zero / zero) == false : "this is not a number";

        assert Double.isInfinite(  - one / zero) == false : "this is positive infinity";

        assert Math.signum( - one / zero) == +1.0 : "signum tells sign of anumber, -1 or +1 or 0 or NaN";

    }

}
