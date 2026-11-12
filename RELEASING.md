# Releasing

## Versioning

The project uses Semantic Versioning. Update `VERSION_NAME` in
`gradle.properties` and `CHANGELOG.md` before a release.

## GitHub and JitPack

1. Commit the release changes.
2. Create a signed tag:

   ```bash
   git tag -s 0.2.0 -m "kmp-bigdecimal 0.2.0"
   git push origin main --tags
   ```

3. Create a GitHub release from the tag.
4. JitPack builds tags automatically. Users can then depend on:

   ```kotlin
   implementation("com.github.sojo-se:kmp-bigdecimal:0.2.0")
   ```

## Maven Central

Maven Central requires a Sonatype namespace and GPG signing. The build already
produces the required publications through `maven-publish`.

1. Verify ownership of the `io.github.sojo-se` namespace through GitHub.
2. Add Maven Central credentials and GPG secrets to your local Gradle
   properties or environment.
3. Add the signing plugin and signing configuration for the release build.
4. Publish with:

   ```bash
   ./gradlew publish
   ```

Keep the credentials out of the repository. The provided CI workflow only
builds and tests; publishing credentials are intentionally not committed.
