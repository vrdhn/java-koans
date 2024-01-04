public class BasicKoan extends Koan.Base {

    @Koan.Doc(
            """
            Let's get used to the koan system. replace '__' by something which makes it pass
            """)
    public static void koanAssert() {
        // Read about assert statement:
        // https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.10
        assert __ == false : "Replace __ so that this boolean expression is true";
    }

    public static void koanAssertaaaa() {
        String s = new String("");
        assert "" == (s) : " == can't compare objects, use equalsTo()";
    }
}
