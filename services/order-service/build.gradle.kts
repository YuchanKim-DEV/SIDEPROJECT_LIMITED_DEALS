plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    // 서킷 브레이커(Resilience4j). 대기열 서버가 느리거나 죽었을 때 주문 스레드가 붙잡히지 않게 한다.
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("com.h2database:h2")
}
