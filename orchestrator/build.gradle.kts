plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    implementation(project(":worker-sdk"))
    implementation(project(":worker-protocol"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")
    implementation("software.amazon.awssdk:dynamodb:2.29.6")
    implementation("org.apache.kafka:kafka-clients:3.7.0")
    implementation("redis.clients:jedis:5.1.5")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.register<Test>("integrationTest") {
    description = "Runs real integration tests against local DynamoDB, Kafka, and Redis."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
    systemProperty("helios.dynamodb.endpoint", System.getProperty("helios.dynamodb.endpoint", "http://host.docker.internal:8000"))
    systemProperty("helios.kafka.bootstrap", System.getProperty("helios.kafka.bootstrap", "host.docker.internal:9092"))
    systemProperty("helios.redis.host", System.getProperty("helios.redis.host", "host.docker.internal"))
}

tasks.register<JavaExec>("realBenchmark") {
    description = "Runs real benchmarks against local DynamoDB, Kafka, and Redis and writes benchmark-results artifacts."
    group = "verification"
    classpath = sourceSets["integrationTest"].runtimeClasspath
    mainClass.set("com.helios.orchestrator.integration.RealBenchmarkRunner")
    workingDir = rootProject.projectDir
    systemProperty("helios.dynamodb.endpoint", System.getProperty("helios.dynamodb.endpoint", "http://host.docker.internal:8000"))
    systemProperty("helios.kafka.bootstrap", System.getProperty("helios.kafka.bootstrap", "host.docker.internal:9092"))
    systemProperty("helios.redis.host", System.getProperty("helios.redis.host", "host.docker.internal"))
    systemProperty("helios.benchmark.outputDir", System.getProperty("helios.benchmark.outputDir", "benchmark-results"))
    systemProperty("helios.benchmark.durationSeconds", System.getProperty("helios.benchmark.durationSeconds", "300"))
    systemProperty("helios.benchmark.targetRatePerSecond", System.getProperty("helios.benchmark.targetRatePerSecond", "500"))
    systemProperty("helios.benchmark.latencySamples", System.getProperty("helios.benchmark.latencySamples", "1000"))
}

tasks.check {
    dependsOn("integrationTest")
}
