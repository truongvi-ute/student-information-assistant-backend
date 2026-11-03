package vn.hcmute.edu.sia.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class RustFsStorageConfig {

    @Bean(destroyMethod = "close")
    public S3Client rustFsS3Client(
            @Value("${app.storage.rustfs.endpoint}") String endpoint,
            @Value("${app.storage.rustfs.region}") String region,
            @Value("${app.storage.rustfs.access-key}") String accessKey,
            @Value("${app.storage.rustfs.secret-key}") String secretKey
    ) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .forcePathStyle(true)
                .build();
    }
}
