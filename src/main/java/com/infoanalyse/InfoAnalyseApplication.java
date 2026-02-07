package com.infoanalyse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.infoanalyse.dao.mapper")
public class InfoAnalyseApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfoAnalyseApplication.class, args);
    }
}