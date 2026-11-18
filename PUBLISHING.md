# Publishing to JetBrains Marketplace

1. `./gradlew buildPlugin` -> `build/distributions/blood-sweat-and-code-0.1.0.zip`
2. Test with `./gradlew runIde`, then `./gradlew verifyPlugin`.
3. Make an account at https://plugins.jetbrains.com, create a vendor profile (name: tipandtale).
4. Upload > "Add new plugin", upload the zip. Pick license MIT, add tags, confirm description.
5. Wait for review (usually 1-3 business days). Email tells you the result.
6. Updates: bump `pluginVersion` in gradle.properties, rebuild, upload new version.
   Automate later: Hub permanent token -> `export PUBLISH_TOKEN=...` -> `./gradlew publishPlugin`.
   The FIRST upload must be manual.

Notes: JLayer (LGPL) is bundled, see NOTICE. The fan audio clips may be taken down by rights holders.
