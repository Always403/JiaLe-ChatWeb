package com.example.jialechatweb.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(String toEmail, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("您的验证码");

        String expireTime = LocalDateTime.now().plusMinutes(5).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String htmlContent = String.format("""
                <div style="font-family: Arial, sans-serif; padding: 20px; border: 1px solid #ddd; border-radius: 8px;">
                    <h2 style="color: #4F46E5;">验证码通知</h2>
                    <p>您好！</p>
                    <p>您的验证码是：</p>
                    <h1 style="color: #4F46E5; font-size: 32px; letter-spacing: 5px;">%s</h1>
                    <p>该验证码将在 <strong>%s</strong> 过期，请尽快使用。</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                    <div style="color: #666; font-size: 12px;">
                        <p>此邮件由系统自动发送，请勿回复。</p>
                        <p>如果您未申请此验证码，请忽略此邮件，您的账户安全不会受到影响。</p>
                        <p style="text-align: center;">© 2026 JiaLe Chat Inc. All rights reserved.</p>
                    </div>
                </div>
                """, code, expireTime);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}
