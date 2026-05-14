# Rule: Java Style

## Language version

Java 21+. Always enable preview features for new projects (`--enable-preview` in build). Embrace modern idioms: records, sealed classes, pattern matching, text blocks, var.

## Tooling

### Gradle (Groovy DSL)

```groovy
// build.gradle
plugins {
    id 'java'
    id 'checkstyle'
    id 'pmd'
    id 'com.github.spotbugs' version '6.1.7'
    id 'jacoco'
}

checkstyle {
    toolVersion = '10.21.0'
    configFile = file('config/checkstyle/google_checks.xml')
    maxWarnings = 0
}

pmd {
    toolVersion = '7.8.0'
    ruleSetFiles = files('config/pmd/ruleset.xml')
    consoleOutput = true
}

spotbugs { effort = 'max'; reportLevel = 'medium' }

jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = 0.80 } } }
}
```

### Maven

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <configLocation>google_checks.xml</configLocation>
        <failsOnError>true</failsOnError>
        <violationSeverity>warning</violationSeverity>
    </configuration>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>

<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.9.3.0</version>
    <configuration><effort>Max</effort><threshold>Medium</threshold></configuration>
    <executions>
        <execution><goals><goal>check</goal></goals></execution>
    </executions>
</plugin>

<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal><goal>report</goal><goal>check</goal></goals>
            <configuration>
                <rules><rule><limits>
                    <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
                </limits></rule></rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

CI must fail on any Checkstyle, SpotBugs, or PMD violation.

## Style Guide

Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). Key points:

- 4-space indent (no tabs)
- Opening brace on same line
- K&R brace style
- Max line length: 100 characters
- One top-level class per file
- `import` statements: no wildcard imports

## Naming

| Element | Convention | Example |
|---|---|---|
| Class / Interface / Record | PascalCase | `UserService`, `UserResult` |
| Method | camelCase verb | `findById`, `createUser` |
| Variable | camelCase | `userId`, `createdAt` |
| Constant | SCREAMING\_SNAKE\_CASE | `MAX_RETRIES`, `DEFAULT_TIMEOUT` |
| Package | lowercase dots | `com.example.users` |
| Generic type param | Single uppercase | `T`, `E`, `K`, `V` |

## Modern Java Idioms

- **Records** for value objects and DTOs — no Lombok `@Data` needed
- **Sealed classes** for domain result types — never throw for expected outcomes
- **Pattern matching** for `switch` and `instanceof` — no unchecked casts
- **`var`** for local variables where the type is obvious from the right-hand side
- **Text blocks** for multi-line strings (JSON, SQL, XML fragments)
- **Virtual threads** (Spring Boot 3.2+: `spring.threads.virtual.enabled=true`) for I/O-bound work

## Lombok Policy

Use Lombok only for:
- `@Slf4j` — logger declaration
- `@RequiredArgsConstructor` — constructor injection in Spring beans

Do **not** use `@Data`, `@Getter`, `@Setter` on mutable objects — use records or explicit accessors. Do not use `@Builder` when records with `with` patterns suffice.

## Forbidden patterns

- `null` returns from public methods — use `Optional<T>` or sealed results
- Raw types (unparameterised generics)
- `instanceof` without pattern matching (Java 16+)
- Checked exceptions for business logic — use unchecked domain exceptions or sealed results
- `System.out.println` in production code — use SLF4J
- Magic numbers and strings — extract to named constants
- `new ArrayList<>()` when the list will be immutable — use `List.of()`

## Checkstyle config

Download Google Checks: `config/checkstyle/google_checks.xml` from the Checkstyle GitHub releases. Customise max line length to 100.
