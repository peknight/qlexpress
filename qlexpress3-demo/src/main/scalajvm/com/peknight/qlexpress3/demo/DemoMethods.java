package com.peknight.qlexpress3.demo;

public class DemoMethods {
    public String getTemplate(Object... params) throws Exception {
        String result = "";
        for (Object obj : params) {
            result = result + obj + ",";
        }
        return result;
    }
}
