package com.liang.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * liang-RAG 知识引擎启动类
 *
 * @author liang
 */
@EnableAsync
@SpringBootApplication
public class LiangRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiangRagApplication.class, args);
    }

}
