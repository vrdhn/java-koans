package koans.decl;

public class LocalVariables {

    /**
     * The next statements we look at are variable declarations,
     * and variable assignments.
     *
     * In it's simplest form, variable declaration has syntax:
     *
     *    data-type  identifier ;
     *
     * And an assignment has the form :
     *
     *    identifier = value;
     *
     * Data-types and literal values  are going to be introduced later,
     * but the usage of  int, String and literarls here should be self explanatory.
     *
     * Note that compiler will generate an error if a local variable is not given
     * a value before the first use;
     *
     */
    public static void declaration() {

        int size;
        String text;

        // The variable value can be (re)assigned any time,
        // (unless they are final, which are are going to see much later )
        text = "Hello World";
        size = 10;
        assert size == text.length() : "size should be set to count of characters in text.";

    }

    /**
     * The variable declarations can have initial value too.
     * The syntax would be :
     *
     *    data-type  identifier = value;
     *
     * In this case , compiler can automatically figure out the data-tpe, and
     * the keyword `var` can be used instead of the data type.
     * Note that 'var' can be used only when a initializer is present.
     */
    public static void initializers () {
        int size = 10;
        var text = "Hello World";
        assert size == text.length() : "size should be set to count of characters in text.";

    }


}
