package com.bluenote.content.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContentStorageConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "bluenote.storage.minio")
    public MinioStorageProperties minioStorageProperties() {
        return new MinioStorageProperties();
    }

    @Bean
    public MinioClient minioClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    public static class MinioStorageProperties {

        private String endpoint;
        private String publicEndpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private int uploadUrlExpiresSeconds;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getPublicEndpoint() {
            return publicEndpoint;
        }

        public void setPublicEndpoint(String publicEndpoint) {
            this.publicEndpoint = publicEndpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public int getUploadUrlExpiresSeconds() {
            return uploadUrlExpiresSeconds;
        }

        public void setUploadUrlExpiresSeconds(int uploadUrlExpiresSeconds) {
            this.uploadUrlExpiresSeconds = uploadUrlExpiresSeconds;
        }
    }
}

