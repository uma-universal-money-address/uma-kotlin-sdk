# UMA Kotlin SDK

The UMA protocol implementation for Kotlin and Java!
See [this repository](https://github.com/lightsparkdev/kotlin-sdk/tree/develop/umaserverdemo) for a sample
implementation and check out
the [full documentation](https://app.lightspark.com/docs/uma-sdk/introduction) for more info.

## Installation

You can install the SDK from Maven Central using Gradle or Maven.

### Gradle

**In Groovy:**

```groovy
dependencies {
    implementation 'me.uma:uma-sdk:0.1.2'
}
```

**In Kotlin:**

```kotlin
dependencies {
    implementation("me.uma:uma-sdk:0.1.2")
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
    <version>0.1.2</version>
</dependency>
</dependencies>
```
