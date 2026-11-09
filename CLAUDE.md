# Downlink

Android app (Kotlin, Compose) for satellite pass prediction and RTL-SDR reception, GPL-3.0.
Published on GitHub (professorengineergit/Downlink), aimed at F-Droid and Google Play.

## Saying that an AI helped

Setting for Downlink, chosen 2026-11-09: level 2, "where it counts".

- README carries one plain sentence that parts are written with an AI assistant and that the
  maintainer decides, tests and answers for every change (section "Wie das entsteht").
- GitHub release notes and PRs say in one line what the assistant did and how it was checked,
  where it did a substantial part.
- Commit messages and code comments stay clean.

## Build notes

- The project lives in an iCloud-synced folder; iCloud creates " 2" duplicates that break Gradle.
  Run `rm -rf app/build` before a build and delete stray "* 2.*" files in `src/`.
- Native code (`app/src/main/cpp`) needs NDK 29.0.14206865 and CMake 3.31.6.
- Release signing reads `downlink.keystore`, `downlink.keystore.password`, `downlink.key.alias`
  and `downlink.key.password` from `local.properties` (never committed).
- LRPT decoder host test: `tools/lrpt` (generator + `test_main.cpp`).
