package com.coremedia.blueprint.boot.contentfeeder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
        "net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration",
        "net.devh.boot.grpc.client.autoconfigure.GrpcClientHealthAutoConfiguration",
        "net.devh.boot.grpc.client.autoconfigure.GrpcClientMetricAutoConfiguration",
})
public class ContentFeederApp {

  // ... Bean definitions
  public static void main(String[] args) {
    SpringApplication.run(ContentFeederApp.class, args);
  }
}
