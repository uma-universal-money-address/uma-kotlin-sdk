# Universal Money Address (UMA) Kotlin SDK

The Universal Money Address (UMA) SDK for Kotlin and Java. UMA is an open protocol that makes sending money as easy as sending an email, using human-readable addresses like $alice@bank.com.

See [this repository](https://github.com/lightsparkdev/kotlin-sdk/tree/develop/umaserverdemo) for a sample
implementation and check out the [full documentation](https://docs.uma.me) for more info.

## Installation

You can install the SDK from Maven Central using Gradle or Maven.

### Gradle

**In Groovy:**

```groovy
dependencies {
    implementation 'me.uma:uma-sdk:1.6.2'
}
```

**In Kotlin:**

```kotlin
dependencies {
    implementation("me.uma:uma-sdk:1.6.2")
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>central</id>
        <url>https://repo1.maven.org/maven2</url>
    </repository>
</repositories>

<dependencies>
<dependency>
    <groupId>me.uma</groupId>
    <artifactId>uma-sdk</artifactId>
    <version>1.6.2</version>
</dependency>
</dependencies>
```
