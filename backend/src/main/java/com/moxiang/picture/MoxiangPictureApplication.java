package com.moxiang.picture;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.moxiang.picture.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class MoxiangPictureApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoxiangPictureApplication.class, args);
    }

}
