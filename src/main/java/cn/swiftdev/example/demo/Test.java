package cn.swiftdev.example.demo;

import java.io.File;

public class Test {
    public static void main(String[] args) {
        Test test = new Test();
        String basePath = test.getClass().getResource("/").toString();
        System.out.println(basePath);
        File file = new File(basePath);
    }
}
