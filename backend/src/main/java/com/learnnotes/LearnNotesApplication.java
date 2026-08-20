package com.learnnotes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * learn-notes 个人学习笔记网站后端入口。
 */
@SpringBootApplication
@MapperScan("com.learnnotes.**.mapper")
@ConfigurationPropertiesScan
public class LearnNotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearnNotesApplication.class, args);
    }
}
