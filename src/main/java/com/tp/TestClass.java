package com.tp;

public class TestClass {
    private int x;

    public void foo() {
        bar();
    }

    public void bar() {
        int y = 10;
        System.out.println(y);
    }
}