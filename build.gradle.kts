// 루트는 공통 설정만 담는다. 실제 앱은 services/ 아래의 각 모듈이다.
plugins {
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.limiteddeals"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(25)
            }
        }

        dependencies {
            "compileOnly"("org.projectlombok:lombok")
            "annotationProcessor"("org.projectlombok:lombok")
            "testCompileOnly"("org.projectlombok:lombok")
            "testAnnotationProcessor"("org.projectlombok:lombok")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }

    // Spring Cloud 버전은 Spring Boot 4.1과 호환되는 2025.1.x로 맞춘다.
    pluginManager.withPlugin("io.spring.dependency-management") {
        extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
            imports {
                mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.4")
            }
        }
    }
}
