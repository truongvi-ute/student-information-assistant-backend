package vn.hcmute.edu.sia.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiServiceConfig {

    @Bean
    public RestClient aiServiceRestClient(
            @Value("${app.ai-service.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.ai-service.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.ai-service.read-timeout-ms:60000}") long readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
