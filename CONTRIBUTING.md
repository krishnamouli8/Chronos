# Contributing to Chronos

Thank you for considering contributing to Chronos! This document provides guidelines for contributing code, tests, documentation, and benchmarks.

---

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Workflow](#development-workflow)
4. [Code Review Checklist](#code-review-checklist)
5. [Testing Requirements](#testing-requirements)
6. [Benchmark Requirements](#benchmark-requirements)
7. [Documentation Standards](#documentation-standards)

---

## Code of Conduct

- Be respectful and constructive in discussions
- Focus on technical merit, not personal opinions
- Help maintain a welcoming community

---

## Getting Started

### Prerequisites

- Java 17+ (OpenJDK recommended)
- Maven 3.8+
- Docker (for integration tests)
- IDE: IntelliJ IDEA or VS Code with Java extensions

### Fork and Clone

```bash
# 1. Fork the repository on GitHub
# 2. Clone your fork
git clone https://github.com/YOUR_USERNAME/Chronos.git
cd Chronos

# 3. Add upstream remote
git remote add upstream https://github.com/krishnamouli8/Chronos.git

# 4. Create feature branch
git checkout -b feature/my-awesome-feature
```

### Build and Test

```bash
# Build
mvn clean package

# Run tests
mvn test

# Run specific test
mvn test -Dtest=ChronosCacheTest

# Check coverage
mvn test jacoco:report
open target/site/jacoco/index.html
```

---

## Development Workflow

### 1. Before You Start

- Check existing issues/PRs to avoid duplicates
- Open an issue to discuss major changes
- Small bug fixes can go straight to PR

### 2. Coding Standards

**Java Style**:

- Follow Google Java Style Guide
- Use IntelliJ default formatter
- Max line length: 120 characters
- Javadoc for public APIs

**Naming Conventions**:

- Classes: `PascalCase`
- Methods/fields: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Packages: `lowercase`

**Example**:

```java
/**
 * Manages cache eviction using LRU policy.
 */
public class LRUEvictionPolicy implements EvictionPolicy {
    private static final int DEFAULT_CAPACITY = 10000;

    private final int maxCapacity;

    public LRUEvictionPolicy(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    @Override
    public CacheEntry selectEvictionCandidate(List<CacheEntry> entries) {
        // Implementation
    }
}
```

### 3. Commit Messages

Follow conventional commits:

```
type(scope): short description

Longer explanation if needed.

Fixes #123
```

**Types**:

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `test`: Adding/updating tests
- `perf`: Performance improvement
- `refactor`: Code restructuring
- `chore`: Build/tooling changes

**Examples**:

```
feat(prefetch): add support for second-order Markov chains

Implement higher-order Markov chains for better accuracy
on complex access patterns. Performance impact: +5% overhead
for 10% accuracy gain.

Fixes #42
```

```
fix(cache): prevent negative TTL values

Add validation in ChronosCache.put() to reject negative TTL.
Throw IllegalArgumentException with clear message.

Fixes #56
```

### 4. Pull Request Process

```bash
# 1. Keep your fork updated
git fetch upstream
git rebase upstream/main

# 2. Push to your fork
git push origin feature/my-awesome-feature

# 3. Open PR on GitHub
# - Fill out PR template
# - Link related issues
# - Add screenshots if UI changes
```

---

## Code Review Checklist

Before submitting PR, ensure:

### Functionality

- [ ] Feature works as intended
- [ ] Edge cases handled
- [ ] No regressions introduced

### Code Quality

- [ ] Follows coding standards
- [ ] No unnecessary complexity
- [ ] Clear variable/method names
- [ ] No magic numbers (use constants)
- [ ] Proper error handling

### Documentation

- [ ] Javadoc for public APIs
- [ ] README updated if needed
- [ ] ADR created for design decisions
- [ ] Inline comments for complex logic

### Testing

- [ ] Unit tests added
- [ ] Integration tests if touching network/storage
- [ ] Coverage remains >= 72%
- [ ] All tests pass locally

### Performance

- [ ] No obvious performance regressions
- [ ] Benchmarks updated if touching hot paths
- [ ] Profiling results if optimization

### Security

- [ ] Input validation added
- [ ] No hardcoded credentials
- [ ] No injection vulnerabilities

---

## Testing Requirements

### Unit Tests

**Requirement**: Every new class must have unit tests.

**Coverage Target**: >= 72% overall

**Example**:

```java
@Test
void testPutAndGet() {
    ChronosCache cache = new ChronosCache(ChronosConfig.defaults());

    byte[] value = "test-value".getBytes();
    cache.put("key1", value, 60);

    byte[] retrieved = cache.get("key1");
    assertArrayEquals(value, retrieved);
}

@Test
void testTTLExpiration() throws InterruptedException {
    ChronosCache cache = new ChronosCache(ChronosConfig.defaults());

    cache.put("key1", "value".getBytes(), 1); // 1 second TTL
    Thread.sleep(1500);

    assertNull(cache.get("key1"), "Entry should have expired");
}
```

### Integration Tests

**When required**: Changes to network, storage, or cross-component features

**Location**: `src/test/java/com/krishnamouli/chronos/integration/`

**Example**:

```java
@Test
void testRedisProtocolCompliance() throws Exception {
    // Start test server
    ChronosServer server = startTestServer(6380);

    // Use real Redis client
    Jedis client = new Jedis("localhost", 6380);

    // Test commands
    client.set("key1", "value1");
    assertEquals("value1", client.get("key1"));

    client.close();
    server.shutdown();
}
```

### Property-Based Tests (jqwik)

**When required**: Core data structure changes

**Example**:

```java
@Property
void putThenGetReturnsValue(@ForAll String key, @ForAll byte[] value) {
    ChronosCache cache = new ChronosCache(ChronosConfig.defaults());

    cache.put(key, value, 0);
    byte[] retrieved = cache.get(key);

    assertArrayEquals(value, retrieved);
}
```

---

## Benchmark Requirements

### When Benchmarks Are Required

Update benchmarks if you:

- Change hot path code (cache operations, locking)
- Add new eviction policy
- Optimize performance
- Change concurrency model

### Running Benchmarks

```bash
# Run all benchmarks
mvn test -Pbenchmark

# Run specific benchmark
mvn test -Pbenchmark -Dtest=ChronosBenchmark#readOnly
```

### Adding New Benchmarks

Use JMH framework:

```java
@State(Scope.Benchmark)
public class MyFeatureBenchmark {

    private ChronosCache cache;

    @Setup
    public void setup() {
        cache = new ChronosCache(ChronosConfig.defaults());
        // Warmup data
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void benchmarkMyFeature(Blackhole bh) {
        byte[] result = cache.myFeature("key");
        bh.consume(result);
    }
}
```

### Benchmark Review Criteria

- [ ] Baseline recorded before change
- [ ] Comparison shows impact (throughput, latency)
- [ ] No regression > 10% without justification
- [ ] Results added to PERFORMANCE.md if significant

---

## Documentation Standards

### Javadoc

**Required for**:

- All public classes
- All public methods
- Complex private methods

**Template**:

```java
/**
 * Brief one-line description.
 *
 * Longer explanation with:
 * - What the component does
 * - When to use it
 * - Key limitations
 *
 * @param paramName Description of parameter
 * @return Description of return value
 * @throws ExceptionType When this exception is thrown
 * @since 1.1.0
 */
public ReturnType methodName(ParamType paramName) throws ExceptionType {
    // Implementation
}
```

### Architecture Decision Records (ADRs)

**When required**: Major design decisions

**Location**: `docs/adr/`

**Template** (`docs/adr/XXX-decision-title.md`):

```markdown
# ADR-XXX: Decision Title

**Status**: Proposed | Accepted | Rejected  
**Date**: YYYY-MM-DD  
**Deciders**: Names

## Context

What is the problem we're solving?

## Decision

What did we decide to do?

## Rationale

Why this approach over alternatives?

## Consequences

What are the trade-offs?

## Alternatives Considered

What else did we evaluate?
```

### README Updates

Update README if you:

- Add user-facing feature
- Change configuration
- Modify deployment process

---

## Pull Request Template

We provide a template that covers:

```markdown
## Description

Brief description of changes.

## Type of Change

- [ ] Bug fix
- [ ] New feature
- [ ] Performance improvement
- [ ] Documentation update

## Checklist

- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] Benchmarks updated (if performance change)
- [ ] ADR created (if design decision)
- [ ] All tests pass

## Testing

How was this tested?

## Performance Impact

Any measurable performance impact?

## Breaking Changes

Any breaking API changes?
```

---

## Community

### Where to Get Help

- GitHub Issues: Bug reports, feature requests
- GitHub Discussions: Questions, ideas
- Pull Requests: Code review, feedback

### Recognition

Contributors will be:

- Listed in Contributors section
- Credited in release notes
- Recognized in commit messages

---

## Release Process (for Maintainers)

1. Update version in `pom.xml`
2. Update CHANGELOG.md
3. Run full test suite + benchmarks
4. Create release tag: `git tag v1.1.0`
5. Push tag: `git push origin v1.1.0`
6. GitHub Actions builds and publishes

---

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
