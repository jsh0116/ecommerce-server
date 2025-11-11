import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    id("jacoco")
}

allprojects {
    group = property("app.group").toString()
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.dependencies.get().toString())
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(libs.spring.boot.starter.web)

    // JPA & ORM
    implementation(libs.spring.boot.starter.data.jpa)

    // Database
    implementation(libs.mysql.connector)
    runtimeOnly(libs.h2)

    // Swagger UI & OpenAPI (Springdoc)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.2")

    annotationProcessor(libs.spring.boot.configuration.processor)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.mockk)
    testImplementation(libs.assertj)
    testImplementation(libs.fixture.monkey.starter.kotlin)
    testImplementation(libs.bundles.testcontainers.mysql)
}

// about source and compilation
java {
    sourceCompatibility = JavaVersion.VERSION_17
}

with(extensions.getByType(JacocoPluginExtension::class.java)) {
    toolVersion = "0.8.7"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        // support JSR 305 annotation ( spring null-safety )
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}
// bundling tasks
tasks.getByName("bootJar") {
    enabled = true
}
tasks.getByName("jar") {
    enabled = false
}
// test tasks
tasks.test {
    ignoreFailures = true
    useJUnitPlatform()
}