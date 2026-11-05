plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // WebFlux(논블로킹) 기반 게이트웨이. 버전은 루트의 Spring Cloud BOM이 정한다.
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Rate Limit 카운터를 Redis에 둔다(게이트웨이가 여러 대여도 한도를 함께 센다).
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    // 뒤쪽 서버가 죽거나 느릴 때 503과 안내 메시지로 빨리 응답한다(논블로킹용 Resilience4j).
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
