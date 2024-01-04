/** All koans should derive from this */
class Koan {

    /**
     * All koan ( i.e. public static void koan*() methods ) should use this to add description. The
     * engine would print this when the koan fails
     */
    public @interface Doc {
        String value();
    }

    /** All koans should derive from this for using the common functions. */
    public static class Base {}
}
