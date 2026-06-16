# Repository Guidelines

## Sources Of Truth
- Follow `CONTRIBUTING.md` for code style, commit messages, PR expectations, and the canonical build/test commands.
- Trust `pom.xml` profiles and plugin config over prose when commands or checks disagree.

## Build And Test Commands
- Use the Maven wrapper from the repo root: `./mvnw ...`.
- Full local verification before a PR is `./mvnw clean package`.
- Publish all modules locally with tests: `./mvnw clean install`.
- Build or test one module plus required upstream modules with `./mvnw -am -pl <module-path> package`, for example `./mvnw -am -pl models/spring-ai-openai package`.
- Run one unit test with Surefire: `./mvnw -am -pl <module-path> -Dtest=ClassName test`.
- Run integration tests only when needed with `-Pintegration-tests verify`; focused example: `./mvnw -am -pl vector-stores/spring-ai-pgvector-store -Pintegration-tests -Dfailsafe.failIfNoSpecifiedTests=false -Dfailsafe.rerunFailingTestsCount=2 -Dit.test=PgVectorStoreIT verify`.
- Run the CI fast integration profile with `./mvnw -Pci-fast-integration-tests verify` when validating the main integration path.
- Format Java sources with `./mvnw process-sources`; it uses Spring Java Format but does not fix import ordering.
- Check Javadocs with `./mvnw javadoc:javadoc` and build reference docs with `./mvnw -pl spring-ai-docs antora`.
- If Maven build cache looks stale, retry once with `./mvnw -Dmaven.build.cache.enabled=false clean package` before changing code.

## Environment
- The build requires Maven `3.9.1+` and a JDK matching `compiler.jdk.version` `[17.0.19,)`; `.sdkmanrc` pins `java=17.0.19-librca`.
- Use a native-architecture JDK on macOS, not Rosetta/emulated, because some dependencies are CPU-architecture sensitive.
- Many `*IT` tests are skipped unless provider API key environment variables are set, such as `OPENAI_API_KEY`.

## Module Layout
- Root `pom.xml` is the authoritative module list for this large Maven reactor.
- Core APIs live in modules such as `spring-ai-model`, `spring-ai-vector-store`, `spring-ai-rag`, `spring-ai-client-chat`, and `spring-ai-commons`.
- Provider implementations live under `models/`, vector-store implementations under `vector-stores/`, Boot auto-configurations under `auto-configurations/`, and dependency-only starters under `starters/`.
- Starters intentionally have no `src/main`; a root Maven profile uses that fact to relax the Spring Boot dependency ban.

## Dependency Rules
- Non-starter, non-autoconfigure production code must not depend on Spring Boot; Boot is allowed for tests and special Boot-facing modules.
- Auto-configuration modules are detected by `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and must keep production dependencies optional unless explicitly exempted by the root enforcer profile.
- Public packages are expected to include `package-info.java` and be annotated for JSpecify nullness; Checkstyle reports missing package info.

## Style Gotchas
- Java, Kotlin, XML, and XSD files use tabs with size 4; Markdown and AsciiDoc use one sentence per line.
- Java source order is license, package, imports, then exactly one top-level class.
- Import groups are `java`, `javax`/`jakarta`, other imports, `org.springframework`, then static imports; wildcard imports are forbidden.
- Static imports are generally forbidden in production code but expected in tests for AssertJ/JUnit-style assertions.
- New public API types and methods need `@since 2.0.0` unless the surrounding code indicates a newer version.

## Git Conventions
- Commit titles start with an uppercase verb, use no `fix:`/`feat:` prefix, and omit issue or PR numbers.
- Commits require a `Signed-off-by` trailer.
