
public class Variable {

    /**
     * In these koans, we are looking at statements. Statements are placed
     * inside the methods. The first of these statement types is variable
     * declaration
     *
     * Variable declaration have this syntax: [ final ] type idenifier [ =
     * initialization ]
     *
     * Note the the 'assert' statement has already been introduced. Other
     * concepts touched upon: 1. Assignment statement ( variable = expression )
     * 2. Integer literal ( sequence of digits ) 3. Long literal ( sequence of
     * digits suffixed by 'L' )
     */
    public static void declaration() {
        int size; // integer it not a valid type, use 'int' here.
        size = 120; // this is standard assigment statement;
        assert size == 120 : "Expected size to be integer of value 120";

        int count = 100; // variable can be initialized
        assert count == 100 : "expected count to be 100";

        final int pi;
        pi = 3;
        //pi = 4;  // final variable can only be set once, comment this line out.
        assert pi == 3 : "Expected pi to be 3";

        final int e = pi - 4; // final and initialiation can be done together, 4 ain't right here.
        assert e == 2 : "Expected e to be 2";

    }

    public static void badDeclarations() {

        int size_2; // can not redeclare a variable, rename to size_2
        size_2 = 100;
        assert size_2 == 100;

    }

}
