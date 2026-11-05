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

    // 9단계: 로그인 토큰(JWT) 만들기와 검증. Spring Security도 내부에서 쓰는 라이브러리(Spring Boot가 버전을 관리하지 않아 직접 고정).
    implementation("com.nimbusds:nimbus-jose-jwt:10.10")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
}
