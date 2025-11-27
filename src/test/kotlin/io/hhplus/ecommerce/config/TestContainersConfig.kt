package io.hhplus.ecommerce.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * TestContainers 설정
 *
 * 통합 테스트를 위한 Docker 기반 MySQL과 Redis 컨테이너 제공
 *
 * 동작 방식:
 * - 로컬: Docker 사용 가능 시 자동으로 컨테이너 시작
 * - CI (GitHub Actions): MySQL과 Redis 서비스로 제공됨 (컨테이너 생성 안 함)
 *
 * 사용 방법:
 * @Import(TestContainersConfig::class)를 테스트 클래스에 추가
 */
@TestConfiguration
class TestContainersConfig {

    companion object {
        @Container
        @JvmStatic
        private val mysqlContainer = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("hhplus_ecommerce_test")
            .withUsername("root")
            .withPassword("root")
            .withExposedPorts(3306)

        @Container
        @JvmStatic
        private val redisContainer = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:mysql://${mysqlContainer.host}:${mysqlContainer.firstMappedPort}/hhplus_ecommerce_test"
            }
            registry.add("spring.datasource.username") { "root" }
            registry.add("spring.datasource.password") { "root" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }

            registry.add("spring.redis.host") { redisContainer.host }
            registry.add("spring.redis.port") { redisContainer.firstMappedPort }
        }
    }
}

