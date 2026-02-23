package com.example.jialechatweb.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
// import com.aliyun.oss.model.LifecycleRule;
// import com.aliyun.oss.model.SetBucketLifecycleRequest;
// import com.aliyun.oss.model.BucketLifecycleConfiguration;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

@Service
public class OssService {

    private static final Logger logger = LoggerFactory.getLogger(OssService.class);

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;
    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;
    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;
    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;
    @Value("${aliyun.oss.url-expiration-seconds:604800}")
    private long urlExpirationSeconds;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        // configureLifecycleRules();
    }

    /*
    private void configureLifecycleRules() {
        try {
            OSS client = getClient();
            if (!client.doesBucketExist(bucketName)) {
                logger.warn("Bucket {} does not exist, skipping lifecycle configuration", bucketName);
                return;
            }

            // Create the rule
            LifecycleRule rule = new LifecycleRule("avatar-cleanup-180-days", "avatar/", LifecycleRule.RuleStatus.Enabled, 180);
            
            // Get existing configuration
            BucketLifecycleConfiguration configuration;
            try {
                configuration = client.getBucketLifecycle(bucketName);
            } catch (Exception e) {
                // If no lifecycle exists, create new
                configuration = new BucketLifecycleConfiguration();
            }
            
            // Check if rule already exists to avoid duplication
            boolean ruleExists = false;
            List<LifecycleRule> rules = configuration.getLifecycleRules();
            if (rules == null) {
                rules = new ArrayList<>();
                configuration.setLifecycleRules(rules);
            }
            
            for (LifecycleRule r : rules) {
                if (r.getId().equals(rule.getId())) {
                    ruleExists = true;
                    break;
                }
            }
            
            if (!ruleExists) {
                rules.add(rule);
                client.setBucketLifecycle(new SetBucketLifecycleRequest(bucketName).withLifecycleConfiguration(configuration));
                logger.info("Configured lifecycle rule: avatar-cleanup-180-days");
            }
            
        } catch (Exception e) {
            // Log but don't fail startup
            logger.warn("Failed to configure lifecycle rules: {}", e.getMessage());
        }
    }
    */

    private OSS getClient() {
        if (ossClient == null) {
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        }
        return ossClient;
    }

    public String uploadAvatar(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : ".jpg";
        
        String objectName = generatePath(userId, extension);
        
        // Retry logic
        int maxRetries = 3;
        Exception lastException = null;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType(file.getContentType());
                metadata.setContentLength(file.getSize());
                
                try (InputStream inputStream = file.getInputStream()) {
                    getClient().putObject(bucketName, objectName, inputStream, metadata);
                }
                
                logger.info("Avatar uploaded successfully: {}", objectName);
                return objectName; // Return key, not full URL
            } catch (Exception e) {
                lastException = e;
                logger.warn("Upload attempt {} failed: {}", i + 1, e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
        logger.error("Failed to upload avatar after {} attempts", maxRetries, lastException);
        throw new RuntimeException("Failed to upload to OSS after " + maxRetries + " attempts", lastException);
    }

    private String generatePath(Long userId, String extension) {
        LocalDate now = LocalDate.now();
        String year = now.format(DateTimeFormatter.ofPattern("yyyy"));
        String month = now.format(DateTimeFormatter.ofPattern("MM"));
        return String.format("avatar/%s/%s/user_%d_avatar%s", year, month, userId, extension);
    }

    public String generateSignedUrl(String objectName) {
        if (objectName == null || objectName.startsWith("http")) return objectName;
        
        Date expiration = new Date(System.currentTimeMillis() + urlExpirationSeconds * 1000);
        URL url = getClient().generatePresignedUrl(bucketName, objectName, expiration);
        return url.toString();
    }
    
    public String generateThumbnailUrl(String objectName, int width, int height) {
        if (objectName == null || objectName.startsWith("http")) return objectName;
        
        Date expiration = new Date(System.currentTimeMillis() + urlExpirationSeconds * 1000);
        String style = String.format("image/resize,m_fill,w_%d,h_%d", width, height);
        
        // OSS SDK might need specific request for style
        // Simplest way is adding process param to generatePresignedUrl if supported, 
        // or constructing request.
        // For basic SDK, we might need to use GetObjectRequest logic or string manipulation.
        // Actually, generatePresignedUrl has a signature that accepts params.
        
        // Let's keep it simple: Just standard URL for now, appending params might invalidate signature if not done via SDK.
        // Correct way with SDK:
        // Generate URL then append x-oss-process? No, it must be signed.
        
        // Using string manipulation on the key before signing? No.
        // We need to pass params to generatePresignedUrl.
        // But the standard method doesn't take params easily in older versions.
        // Let's assume standard access for now to avoid complexity, or check SDK version. 3.17.4 is recent.
        
        // Hack: process parameter in Request
        com.aliyun.oss.model.GeneratePresignedUrlRequest request = 
            new com.aliyun.oss.model.GeneratePresignedUrlRequest(bucketName, objectName);
        request.setExpiration(expiration);
        request.setProcess(style);
        
        return getClient().generatePresignedUrl(request).toString();
    }
}
