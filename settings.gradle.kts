rootProject.name = "limited-deals"

// 서비스마다 따로 실행되는 스프링 부트 앱. 코드는 services/ 아래에 둔다.
val services = listOf("gateway", "queue-service", "order-service", "notification-service", "payment-service")
services.forEach { name ->
    include(name)
    project(":$name").projectDir = file("services/$name")
}
