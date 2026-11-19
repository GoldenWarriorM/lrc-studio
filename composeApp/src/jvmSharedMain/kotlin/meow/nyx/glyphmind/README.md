# Glyphmind (vendored)

Lyric romanization engine and language detector from
[nyxiereal/glyphmind](https://github.com/nyxiereal/glyphmind), version 0.1.0,
copied from `src/commonMain/kotlin/meow/nyx/glyphmind`.

Why the sources live here instead of a Gradle dependency: the published JVM
artifact is compiled with Kotlin 2.4 (metadata 2.4.0) for Java 21 (class file
65), while this app builds with Kotlin 2.0.21 and targets Java 17 so the
desktop build keeps starting on a Java 17 runtime. Vendoring the sources keeps
the bytecode level and Kotlin version of the app unchanged.

Local modifications to the upstream sources:

- `DictionaryPacks.kt` no longer depends on `kotlinx-io`; the filesystem work
  (size check, SHA-256 verification, staging directory, atomic move) uses
  `java.io` plus `java.nio.file`, with the same public API and behaviour.
- `DetectorModel.kt` opts into `kotlin.io.encoding.ExperimentalEncodingApi`,
  which Kotlin 2.0.21 still requires for `Base64`.

Language packs are opened from `src/jvmSharedMain/resources/packs/` as
`<pack id>.gz` (`japanese-v1.gz`, `mandarin-v1.gz`), so the app never needs the
network to romanize; they are the release assets pinned in `Dictionaries`,
gzipped. They derive from IPADIC and Unihan data, so `licenses/` ships their
notices alongside this README.

License: GPL-3.0-only, see `LICENSE.txt` and `NOTICE.md` next to this file. The
app itself is distributed under AGPL-3.0, which the GPL-3.0-only terms allow
for the combined work.
