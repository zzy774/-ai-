package com.labreport.server;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class GenPassword {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode("admin123"));
    }
}
