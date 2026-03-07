package com.ecommerce.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication is actually THREE annotations combined into one:
//
// 1. @Configuration     — marks this class as a source of bean definitions
//                         (beans = objects that Spring creates and manages for you)
//
// 2. @EnableAutoConfiguration — tells Spring Boot to automatically configure
//                               your app based on the dependencies in pom.xml.
//                               You added spring-boot-starter-web? Spring auto-configures
//                               an embedded Tomcat server. You didn't have to do anything.
//
// 3. @ComponentScan     — tells Spring to scan this package and all sub-packages
//                         for classes annotated with @Controller, @Service, @Repository, etc.
//                         This is WHY the folder structure matters — everything must be
//                         under com.ecommerce.userservice for Spring to find it.

@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        // SpringApplication.run() does everything:
        // 1. Creates the Spring application context (the container that holds all your beans)
        // 2. Scans for components (@Controller, @Service, etc.)
        // 3. Auto-configures based on your dependencies
        // 4. Starts the embedded Tomcat server
        // 5. Your app is now listening for HTTP requests
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
