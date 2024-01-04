# java koans


Requires java 21:
```
$ java --version
openjdk 21.0.1 2023-10-17 LTS
```

To run:
```
java Run.java
```

Use any editor or IDE to edit the koans in `koans/*Koan.java` files


# For Koan authors

## Folder layout

```
<GIT-ROOT> +- Run.java  <<-- User invokes this with java
           +- build/    <<-- the classes are compiled here.
           +- koans/    <<-- The java sources
```

## Java sources requirements

Java sources in `koans/` can be koans or helper java files.
Right now, there is no `package`  statement put in any of the files.

All the `Koans` sources should have a `public class Koan.... extends Koan.Base {`
signature. All other helper sources should not have a `public` declarations.

`Run.java` compiles each sources from `koans/`  individually, and it needs a 
sequence, to ensure that helper classes are compiled first. This is achieved by 
putting a `@Koan.Order(<integer>)` annotation on the class. Note that this is
used while scanning the java sources, before compiling them.




```
@Koan.Order(500)
public class KoanSomething {

    @Koan.Desc("""
      description
     """)
     public static void koanSomething() {
         ...
     }
 }
 ```


