# Contributing

Contributions are welcome.

## Development

Requirements:

- JDK 17 or newer
- Android SDK for the Android target
- Xcode on macOS for the iOS targets

Run the platform tests:

```bash
./gradlew :jvmTest
./gradlew :jsNodeTest
./gradlew :wasmJsNodeTest
./gradlew :testDebugUnitTest
```

Run the JVM tests before opening a pull request. They include randomized
differential tests against `java.math.BigDecimal`.

## Pull requests

- Keep the public API consistent with the existing BigDecimal conventions.
- Add tests for new arithmetic or rounding behaviour.
- Update `CHANGELOG.md`.
- Do not copy code from another big-number library. By submitting a pull
  request you confirm that you have the right to contribute the code under the
  MIT license used by this project.

## License

By contributing, you agree that your contribution is licensed under the MIT
License in `LICENSE`.
