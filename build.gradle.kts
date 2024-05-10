// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt

plugins {
  val kotlinVersion = "1.9.23"
  kotlin("jvm") version kotlinVersion
  id("com.diffplug.spotless") version "6.25.0"
  id("io.gitlab.arturbosch.detekt") version "1.23.5"
  id("pl.allegro.tech.build.axion-release") version "1.15.5"
  `maven-publish`
  // Apply the java-library plugin for API and implementation separation.
  `java-library`
}

scmVersion { tag { prefix.set("") } }

val kotlinJvmTarget = 21

java { toolchain { languageVersion.set(JavaLanguageVersion.of(kotlinJvmTarget)) } }

publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/Cosmo-Tech/cosmotech-api-azure")
      credentials {
        username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
        password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
      }
    }
  }

  publications {
    create<MavenPublication>("maven") {
      groupId = "com.github.Cosmo-Tech"
      artifactId = "cosmotech-api-azure"
      version = scmVersion.version
      pom {
        name.set("Cosmo Tech API azure")
        description.set("Cosmo Tech API Azure library for Platform")
        url.set("https://github.com/Cosmo-Tech/cosmotech-api-azure")
        licenses {
          license {
            name.set("MIT License")
            url.set("https://github.com/Cosmo-Tech/cosmotech-api-azure/blob/main/LICENSE")
          }
        }
      }

      from(components["java"])
    }
  }
}

repositories {
  maven {
    name = "GitHubPackages"
    url = uri("https://maven.pkg.github.com/Cosmo-Tech/cosmotech-api-common")
    credentials {
      username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
      password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
    }
  }

  mavenCentral()
}

tasks.withType<JavaCompile>() { options.compilerArgs.add("-parameters") }

configure<SpotlessExtension> {
  isEnforceCheck = false

  val licenseHeaderComment =
      """
        // Copyright (c) Cosmo Tech.
        // Licensed under the MIT license.
      """
          .trimIndent()

  java {
    googleJavaFormat()
    target("**/*.java")
    licenseHeader(licenseHeaderComment)
  }
  kotlin {
    ktfmt("0.41")
    target("**/*.kt")
    licenseHeader(licenseHeaderComment)
  }
  kotlinGradle {
    ktfmt("0.41")
    target("**/*.kts")
    //      licenseHeader(licenseHeaderComment, "import")
  }
}

tasks.withType<Detekt>().configureEach {
  buildUponDefaultConfig = true // preconfigure defaults
  allRules = false // activate all available (even unstable) rules.
  config.from(file("$rootDir/.detekt/detekt.yaml"))
  jvmTarget = kotlinJvmTarget.toString()
  ignoreFailures = project.findProperty("detekt.ignoreFailures")?.toString()?.toBoolean() ?: false
  // Specify the base path for file paths in the formatted reports.
  // If not set, all file paths reported will be absolute file path.
  // This is so we can easily map results onto their source files in tools like GitHub Code
  // Scanning
  basePath = rootDir.absolutePath
  reports {
    html {
      // observe findings in your browser with structure and code snippets
      required.set(true)
      outputLocation.set(
          file("${layout.buildDirectory.get()}/reports/detekt/${project.name}-detekt.html"))
    }
    xml {
      // checkstyle like format mainly for integrations like Jenkins
      required.set(false)
      outputLocation.set(
          file("${layout.buildDirectory.get()}/reports/detekt/${project.name}-detekt.xml"))
    }
    txt {
      // similar to the console output, contains issue signature to manually edit baseline files
      required.set(true)
      outputLocation.set(
          file("${layout.buildDirectory.get()}/reports/detekt/${project.name}-detekt.txt"))
    }
    sarif {
      // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations
      // with Github Code Scanning
      required.set(true)
      outputLocation.set(
          file("${layout.buildDirectory.get()}/reports/detekt/${project.name}-detekt.sarif"))
    }
  }
}

tasks.jar {
  manifest {
    attributes(
        mapOf("Implementation-Title" to project.name, "Implementation-Version" to project.version))
  }
}

// Dependencies version

// Required versions
val jacksonVersion = "2.15.3"
val springWebVersion = "6.1.4"
val springBootVersion = "3.2.2"

// Implementation
val cosmotechApiCommonVersion = "1.0.0-SNAPSHOT"
val azureSpringBootBomVersion = "3.14.0"
val azureSDKBomVersion = "1.2.7"
val azureKustoIngestVersion = "3.2.0"

val zalandoSpringProblemVersion = "0.27.0"
val springOauthAutoConfigureVersion = "2.6.8"
val springSecurityJwtVersion = "1.1.1.RELEASE"
val springOauthVersion = "6.2.2"
val springBootStarterWebVersion = "3.2.2"

// Checks
val detektVersion = "1.23.5"

// Tests
val jUnitBomVersion = "5.10.0"
val mockkVersion = "1.13.8"
val awaitilityKVersion = "4.2.0"

dependencies {
  constraints { implementation("org.yaml:snakeyaml:2.2") }
  detekt("io.gitlab.arturbosch.detekt:detekt-cli:$detektVersion")
  detekt("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")

  // Align versions of all Kotlin components
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

  // Use the Kotlin JDK 8 standard library.
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  api("com.github.Cosmo-Tech:cosmotech-api-common:$cosmotechApiCommonVersion")
  // https://mvnrepository.com/artifact/com.azure.spring/spring-cloud-azure-dependencies
  implementation("com.azure.spring:spring-cloud-azure-dependencies:5.12.0")
  // https://mvnrepository.com/artifact/com.azure.spring/spring-cloud-azure-starter-storage-blob
  implementation("com.azure.spring:spring-cloud-azure-starter-storage-blob:5.7.0")
  // https://mvnrepository.com/artifact/com.azure.spring/spring-cloud-azure-starter-storage
  implementation("com.azure.spring:spring-cloud-azure-starter-storage:5.7.0")
  // https://mvnrepository.com/artifact/com.azure/azure-storage-blob-batch
  implementation("com.azure:azure-storage-blob-batch:12.20.1")

  // https://mvnrepository.com/artifact/com.azure.spring/spring-cloud-azure-autoconfigure
  implementation("com.azure.spring:spring-cloud-azure-autoconfigure:5.7.0")
  // https://mvnrepository.com/artifact/com.microsoft.azure.kusto/kusto-ingest
  implementation("com.microsoft.azure.kusto:kusto-ingest:5.0.2")
  // https://mvnrepository.com/artifact/com.azure/azure-containers-containerregistry
  implementation("com.azure:azure-containers-containerregistry:1.2.2")
  // https://mvnrepository.com/artifact/com.azure.spring/spring-cloud-azure-starter-eventhubs
  implementation("com.azure.spring:spring-cloud-azure-starter-eventhubs:5.7.0")

  implementation(
      "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:${springOauthAutoConfigureVersion}") {
        constraints {
          implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
          implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
          implementation("org.springframework:spring-web:$springWebVersion")
          implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
        }
      }
  implementation(
      "org.springframework.boot:spring-boot-starter-security:${springOauthAutoConfigureVersion}")
  implementation("org.springframework.security:spring-security-oauth2-jose:${springOauthVersion}")
  implementation(
      "org.springframework.security:spring-security-oauth2-resource-server:${springOauthVersion}")
  implementation("org.springframework.security:spring-security-jwt:${springSecurityJwtVersion}")
  implementation(
      "org.springframework.boot:spring-boot-starter-web:${springBootStarterWebVersion}") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
      }

  implementation(
      "org.springframework.boot:spring-boot-starter-actuator:$springBootStarterWebVersion")

  testImplementation(kotlin("test"))
  testImplementation(platform("org.junit:junit-bom:${jUnitBomVersion}"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("io.mockk:mockk:${mockkVersion}")
  testImplementation("org.awaitility:awaitility-kotlin:${awaitilityKVersion}")

  // Use the Kotlin test library.
  testImplementation("org.jetbrains.kotlin:kotlin-test")

  // Use the Kotlin JUnit integration.
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
