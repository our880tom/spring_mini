package cn.swiftdev.example.demo;

import cn.swiftdev.example.mvc.annotation.LLController;
import cn.swiftdev.example.mvc.annotation.LLHandlerMapping;
import cn.swiftdev.example.mvc.annotation.LLRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@LLController
@LLHandlerMapping("/demo")
public class DemoAction {

    @LLHandlerMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response, @LLRequestParam("name") String name){
        try {
            response.getWriter().write("Hello,world");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
