package org.jetbrains.eval4j.jdi.test;

public class Debugee {
    public static void main(String[] args) {
        // BREAKPOINT
        Runtime.getRuntime();
        System.out.println("hi");
    }

    public static void foo() {
        System.out.println();
    }
}
