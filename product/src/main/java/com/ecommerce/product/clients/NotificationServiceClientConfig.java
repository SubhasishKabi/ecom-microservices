package com.ecommerce.product.clients;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

//REST CLIENT WITH HTTP INTERFACE
@Configuration
public class NotificationServiceClientConfig {

    @Bean
    public NotificationServiceClient notificationServiceInterface(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        // NOTE: deliberately no "4xx -> empty" status handler here. The stock-alert consumer deletes a
        // subscription only after a successful call, so any non-2xx response must surface as an exception
        // (otherwise a 4xx would silently drop the user's subscription without notifying them).
        RestClient restClient = restClientBuilder.clone()
                .baseUrl("http://notification-service")
                .requestFactory(requestFactory)
                .build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(adapter)
                .build();
        return factory.createClient(NotificationServiceClient.class);
    }
}
