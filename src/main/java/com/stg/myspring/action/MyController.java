package com.stg.myspring.action;

import annotioned.GPAutowired;
import annotioned.GPController;
import annotioned.GPRequestMapping;
import annotioned.GPRequestParam;
import com.stg.myspring.service.MyService;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author songtg3
 * @createTime 2020/2/21
 * @description
 */
@GPController
@GPRequestMapping("demo")
public class MyController {


    @GPAutowired
    private MyService myService;

    @GPRequestMapping("/list")
    public void list(HttpServletRequest request, HttpServletResponse response,
                     @GPRequestParam("name") String name) throws IOException {
        String result = "My name is " + name;
        response.getWriter().println(result);
    }

    @GPRequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response,
                    @GPRequestParam("name") String name) throws IOException {
        String result = "new name is " + name;
        response.getWriter().println(result);
    }
}
