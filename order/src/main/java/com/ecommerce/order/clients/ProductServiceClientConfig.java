package com.ecommerce.order.clients;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.Optional;


//REST CLIENT WITH HTTP INTERFACE
@Configuration
public class ProductServiceClientConfig {
    @Bean
    public ProductServiceClient productServiceInterface(RestClient.Builder restClientBuilder) {
        //Create the RestClient
        RestClient restClient = restClientBuilder
                            .baseUrl("http://product-service")
                //and configures 4xx responses to be handled by returning Optional.empty() instead of the normal error handling.
                            .defaultStatusHandler(HttpStatusCode::is4xxClientError,
                                        ((request, response) -> Optional.empty()))
                            .build();
        //RestClientAdapter: An adapter that connects a RestClient to Spring's HTTP Interface system,
        // allowing interfaces with @GetExchange, @PostExchange, etc. to use that RestClient underneath.
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                                            .builderFor(adapter)
                                            .build();
        return factory.createClient(ProductServiceClient.class);
        //reates a runtime proxy/implementation of the ProductServiceClient interface.
    }
}

//Direct RestClient: You manually write the HTTP request using restClient.get(), .uri(), and .retrieve().
//
//HTTP Interface: You define an interface method using @GetExchange, and Spring generates the HTTP call using RestClient underneath.
//        Both: They support service discovery and load balancing; the main difference is how you write the HTTP call.