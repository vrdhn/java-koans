public class BasicKoan {

    /**
     * Let's get used to the koan system. replace '__' by something which makes it pass and more
     * comment and more commnet
     *
     * <p>and more
     */
    public static void koanAssert() {
        // Read about assert statement:
        // https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.10
        assert __ == false : "Replace __ so that this boolean expression is true";
    }

    /** doc comment */
    public static void koanAssertaaaa() {
        String s = new String("");
        assert s == "" : " == can't compare objects, use equalsTo()";
    }
}
