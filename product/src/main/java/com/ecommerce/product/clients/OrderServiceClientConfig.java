package com.ecommerce.product.clients;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;
import java.util.Optional;

//REST CLIENT WITH HTTP INTERFACE
@Configuration
public class OrderServiceClientConfig {

    @Bean
    public OrderServiceClient orderServiceInterface(RestClient.Builder restClientBuilder) {
        // Short timeouts: verified-purchase is only a label, a slow order-service must never stall review submission
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        // clone() so the shared @LoadBalanced builder bean is not mutated (baseUrl/handlers) for other clients
        RestClient restClient = restClientBuilder.clone()
                .baseUrl("http://order-service")
                .requestFactory(requestFactory)
                .defaultStatusHandler(HttpStatusCode::is4xxClientError,
                        ((request, response) -> Optional.empty()))
                .build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(adapter)
                .build();
        return factory.createClient(OrderServiceClient.class);
    }
}
