package com.example.jialechatweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

@SpringBootApplication
public class JiaLeChatWebApplication {

    public static void main(String[] args) {
        // 配置 JEP-290 序列化过滤器，允许 java.*, javax.* 以及本项目包下的类进行反序列化，拒绝其他所有类
        if (System.getProperty("jdk.serialFilter") == null) {
            System.setProperty("jdk.serialFilter", "java.**;javax.**;org.springframework.**;com.example.jialechatweb.**;!*");
        }
        SpringApplication.run(JiaLeChatWebApplication.class, args);
    }

    @Bean
    public Clock ntpClock() {
        // Try NTP first
        try {
            String timeServer = "ntp.aliyun.com"; // Use Aliyun NTP server for China
            NTPUDPClient timeClient = new NTPUDPClient();
            timeClient.setDefaultTimeout(3000);
            InetAddress inetAddress = InetAddress.getByName(timeServer);
            TimeInfo timeInfo = timeClient.getTime(inetAddress);
            long returnTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();
            long offset = returnTime - System.currentTimeMillis();
            
            System.out.println("NTP Sync Success: offset=" + offset + "ms");
            // Return a clock that adds the offset to the system time
            return Clock.offset(Clock.systemUTC(), java.time.Duration.ofMillis(offset));
        } catch (Exception e) {
            System.err.println("NTP Sync Failed (" + e.getMessage() + "), trying HTTP fallback...");
        }

        // Fallback to HTTP HEAD request (e.g. www.baidu.com)
        try {
            java.net.URL url = new java.net.URL("https://www.baidu.com");
            java.net.URLConnection conn = url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();
            long httpDate = conn.getDate(); // Returns time in milliseconds
            if (httpDate > 0) {
                long offset = httpDate - System.currentTimeMillis();
                System.out.println("HTTP Time Sync Success: offset=" + offset + "ms");
                return Clock.offset(Clock.systemUTC(), java.time.Duration.ofMillis(offset));
            }
        } catch (Exception e) {
            System.err.println("HTTP Time Sync Failed: " + e.getMessage());
        }

        // Last resort: System clock (UTC)
        System.err.println("All time sync methods failed. Using system clock.");
        return Clock.systemUTC();
    }
}
