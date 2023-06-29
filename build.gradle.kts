// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt

plugins {
  val kotlinVersion = "1.8.0"
  kotlin("jvm") version kotlinVersion
  id("com.diffplug.spotless") version "6.12.0"
  id("io.gitlab.arturbosch.detekt") version "1.23.0"
  id("pl.allegro.tech.build.axion-release") version "1.14.3"
  `maven-publish`
  // Apply the java-library plugin for API and implementation separation.
  `java-library`
}

scmVersion { tag { prefix.set("") } }

val kotlinJvmTarget = 17

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
      outputLocation.set(file("$buildDir/reports/detekt/${project.name}-detekt.html"))
    }
    xml {
      // checkstyle like format mainly for integrations like Jenkins
      required.set(false)
      outputLocation.set(file("$buildDir/reports/detekt/${project.name}-detekt.xml"))
    }
    txt {
      // similar to the console output, contains issue signature to manually edit baseline files
      required.set(true)
      outputLocation.set(file("$buildDir/reports/detekt/${project.name}-detekt.txt"))
    }
    sarif {
      // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations
      // with Github Code Scanning
      required.set(true)
      outputLocation.set(file("$buildDir/reports/detekt/${project.name}-detekt.sarif"))
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
// Implementation
val cosmotechApiCommonVersion = "0.1.39-SNAPSHOT"
val azureSpringBootBomVersion = "3.14.0"
val azureSDKBomVersion = "1.2.7"
val azureKustoIngestVersion = "3.2.0"

val zalandoSpringProblemVersion = "0.27.0"
val springOauthAutoConfigureVersion = "2.6.8"
val springSecurityJwtVersion = "1.1.1.RELEASE"
val springOauthVersion = "5.8.3"
val springBootStarterWebVersion = "2.7.11"

// Tests
val jUnitBomVersion = "5.9.1"
val mockkVersion = "1.13.2"
val awaitilityKVersion = "4.2.0"

dependencies {
  detekt("io.gitlab.arturbosch.detekt:detekt-cli:1.21.0")
  detekt("io.gitlab.arturbosch.detekt:detekt-formatting:1.21.0")

  // Align versions of all Kotlin components
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

  // Use the Kotlin JDK 8 standard library.
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  api("com.github.Cosmo-Tech:cosmotech-api-common:$cosmotechApiCommonVersion")
  implementation(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))
  api(platform("com.azure:azure-sdk-bom:$azureSDKBomVersion"))
  implementation("com.azure.spring:azure-spring-boot-starter-storage")
  api("com.azure:azure-storage-blob")
  api("com.azure:azure-storage-blob-batch")
  implementation("com.azure.spring:azure-spring-boot-starter-active-directory")
  implementation("com.microsoft.azure.kusto:kusto-ingest:$azureKustoIngestVersion") {
    exclude(group = "org.slf4j", module = "slf4j-api")
    because(
        "this depends on org.slf4j:slf4j-api 1.8.0-beta4 (pre 2.x)," +
            "which is not backward-compatible with 1.7.x." +
            "See http://www.slf4j.org/faq.html#changesInVersion200")
  }
  implementation("com.azure:azure-messaging-eventhubs")
  implementation("com.azure:azure-identity")

  implementation("org.zalando:problem-spring-web-starter:${zalandoSpringProblemVersion}")
  implementation(
      "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:${springOauthAutoConfigureVersion}")
  implementation("org.springframework.security:spring-security-jwt:${springSecurityJwtVersion}")
  implementation("org.springframework.security:spring-security-oauth2-jose:${springOauthVersion}")
  implementation(
      "org.springframework.security:spring-security-oauth2-resource-server:${springOauthVersion}")
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
