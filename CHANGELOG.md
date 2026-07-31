# Changelog

## [0.13.0](https://github.com/blu3berry-why/Kraft/compare/0.12.0...0.13.0) (2026-07-31)


### Features

* support Android modules using AGP 9 built-in Kotlin ([#103](https://github.com/blu3berry-why/Kraft/issues/103)) ([c855763](https://github.com/blu3berry-why/Kraft/commit/c85576370205cf5a282724be4c734fae59e12cdf))


### Bug Fixes

* correct the misleading parameterized-converter error message ([#110](https://github.com/blu3berry-why/Kraft/issues/110)) ([30e4446](https://github.com/blu3berry-why/Kraft/commit/30e44464289888c286fb55c6f492cee2bd390afe))
* stop silently dropping @OptIn markers in generated code ([#107](https://github.com/blu3berry-why/Kraft/issues/107)) ([2a157a2](https://github.com/blu3berry-why/Kraft/commit/2a157a26227101807f9e41b742b4898ec797598d)), closes [#104](https://github.com/blu3berry-why/Kraft/issues/104) [#106](https://github.com/blu3berry-why/Kraft/issues/106)
* thread non-null bridges through nullable scalar fields ([#108](https://github.com/blu3berry-why/Kraft/issues/108)) ([b7b00b7](https://github.com/blu3berry-why/Kraft/commit/b7b00b7fed27e55d966ce8e22fa6a14cc0023f6d))

## [0.12.0](https://github.com/blu3berry-why/Kraft/compare/0.11.0...0.12.0) (2026-07-18)


### Features

* Kotlin JVM and Android support in the Kraft Gradle plugin ([#93](https://github.com/blu3berry-why/Kraft/issues/93)) ([0ceb9af](https://github.com/blu3berry-why/Kraft/commit/0ceb9af32f60795b2a2467eb148f34733d10a41d))
* Kraft Gradle plugin with typed kraft { } DSL ([#91](https://github.com/blu3berry-why/Kraft/issues/91)) ([3fae12b](https://github.com/blu3berry-why/Kraft/commit/3fae12b63535f9e37d312c27e1cdb084961b0a33))
