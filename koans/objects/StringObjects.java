package koans.objects;

public class StringObjects {

    /**
     * The second type ( besides the primitive types ) a variable can have,
     * is a reference to an object.  In Java , every object is instance of some Class
     * (unlike say javascript, where object is just a collection of properties and values ),
     * and the name of class is used as type for the variable.
     *
     * Here we use java.lang.String ( or just String ) class to get introduced to reference types.
     * Java  treats string literals a bit specially, creating the objects of class
     * java.lang.String as and when needed.
     *
     */
    public static void stringLiterals() {

        // A 'string literal' in java creates an object to type String,
        // hello is a reference to an object ot type String.
        String  hello = "Hello World";

        // Later hello can point to some other string.
        hello = "Hello Universe";

        // Every class defines several ''methods'', which are called using 'dot' notation.
        // java doc lists them: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html
        assert hello.length() == 13 : "How may characters are there in [" + hello + "] ?";

        // Strings literals can be concatenated using '+'
        String a = "Hello" + " World";

        // String concatenation convert anything to string.
        // Note that  '+' is ''right associative'', meaning that the first '+' is evaluated first.
        String sum = "sum of 1 and 1 is " +  1 + 1;
        // System.out.println(sum);

        assert sum.equals("sum of 1 and 1 is 2") : "parenthesis can change evaluation order";

        // Newest version of java can have multiline strings.
        // The amount of leading space of closing triple quote is important.
        // You may want to try these in jshell.
        String singleLineString = "123\n456\n789\n";
        String multiLineString = """
                                   123
                                   456
                                   789
                                """;

        // uncomment this for printing the string
        // System.out.println(multiLineString.replace("\n","\\n"));
        assert multiLineString.equals(singleLineString) : "The closing triple quote need to align with line above it";

    }
}
