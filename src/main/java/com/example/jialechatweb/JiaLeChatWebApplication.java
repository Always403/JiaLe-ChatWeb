package com.example.jialechatweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JiaLeChatWebApplication {

    public static void main(String[] args) {
        // 配置 JEP-290 序列化过滤器，允许 java.*, javax.* 以及本项目包下的类进行反序列化，拒绝其他所有类
        if (System.getProperty("jdk.serialFilter") == null) {
            System.setProperty("jdk.serialFilter", "java.**;javax.**;org.springframework.**;com.example.jialechatweb.**;!*");
        }
        SpringApplication.run(JiaLeChatWebApplication.class, args);
    }

}
