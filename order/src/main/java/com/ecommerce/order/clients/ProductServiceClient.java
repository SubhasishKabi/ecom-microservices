package com.ecommerce.order.clients;

import com.ecommerce.order.dtos.ProductResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface ProductServiceClient {

    @GetExchange("/api/products/{id}")
    ProductResponse getProductDetails(@PathVariable String id);
}

//    // GET - all products
//    @GetExchange("/api/products")
//    List<ProductResponse> getProducts();
//
//    // POST
//    @PostExchange("/api/products")
//    ProductResponse createProduct(@RequestBody ProductRequest request);
//
//    // PUT
//    @PutExchange("/api/products/{id}")
//    ProductResponse updateProduct(
//            @PathVariable String id,
//            @RequestBody ProductRequest request);
//
//    // PATCH
//    @PatchExchange("/api/products/{id}")
//    ProductResponse partiallyUpdateProduct(
//            @PathVariable String id,
//            @RequestBody ProductRequest request);
//
//    // DELETE
//    @DeleteExchange("/api/products/{id}")
//    void deleteProduct(@PathVariable String id);
















//Yes — RestClient is absolutely being used here. The difference is who writes the HTTP-call code.
//
//Normal RestClient approach
//
//You directly use RestClient:
//
//ProductResponse product = restClient.get()
//        .uri("/api/products/{id}", id)
//        .retrieve()
//        .body(ProductResponse.class);
//
//You are manually telling RestClient:
//
//GET this URL → retrieve response → convert it to ProductResponse.


//our current approach
//
//Here:
//
//RestClient restClient = restClientBuilder
//        .baseUrl("http://product-service")
//        .build();
//
//you create the RestClient, but you don't directly call:
//
//        restClient.get()
//
//Instead, you pass it to:
//
//RestClientAdapter adapter = RestClientAdapter.create(restClient);
//
//Then:
//
//HttpServiceProxyFactory factory =
//        HttpServiceProxyFactory
//                .builderFor(adapter)
//                .build();
//
//And finally:
//
//        return factory.createClient(ProductServiceClient.class);
//
//Spring creates an implementation of this interface:
//
//public interface ProductServiceClient {
//
//    @GetExchange("/api/products/{id}")
//    ProductResponse getProductDetails(@PathVariable String id);
//}
//
//So when you later do:
//
//        productServiceClient.getProductDetails("123");
//
//Spring internally performs approximately:
//
//        productServiceClient.getProductDetails("123")
//                 ↓
//HTTP Interface Proxy
//                 ↓
//RestClient
//                 ↓
//GET http://product-service/api/products/123
//        ↓
//Load Balancer
//                 ↓
//Product Service
//So what's the difference?