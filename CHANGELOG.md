# Changelog

## [0.14.1](https://github.com/blu3berry-why/Kraft/compare/0.14.0...0.14.1) (2026-11-25)


### Bug Fixes

* import same-name extension mappers instead of fully qualifying them ([#148](https://github.com/blu3berry-why/Kraft/issues/148)) ([f494162](https://github.com/blu3berry-why/Kraft/commit/f4941628a624d0afd0d3b18b3df940c1e5984eb1))
* propagate opt-in markers from cross-module converters (roadmap [#17](https://github.com/blu3berry-why/Kraft/issues/17)) ([#154](https://github.com/blu3berry-why/Kraft/issues/154)) ([af09709](https://github.com/blu3berry-why/Kraft/commit/af097094eacc48a39fd23c68b64e28f5827ad484))
* report KRAFT-E011 for type-parameter source properties instead of crashing ([#147](https://github.com/blu3berry-why/Kraft/issues/147)) ([78cd030](https://github.com/blu3berry-why/Kraft/commit/78cd030ae95746b71605ea4219227f9635257f75))
* report reverse-only rules on a @MapConfig without @MapReverse ([#150](https://github.com/blu3berry-why/Kraft/issues/150)) ([b737268](https://github.com/blu3berry-why/Kraft/commit/b73726880860f6ebc09277101f4508499e8154c5))
* warn when @FieldMapping rules for one target resolve last-wins ([#151](https://github.com/blu3berry-why/Kraft/issues/151)) ([da9664c](https://github.com/blu3berry-why/Kraft/commit/da9664c5d72dd83c79e9a1742a8540bbba04b902))

## [0.14.0](https://github.com/blu3berry-why/Kraft/compare/0.13.0...0.14.0) (2026-10-27)


### Features

* auto wrap and unwrap value classes ([#114](https://github.com/blu3berry-why/Kraft/issues/114)) ([0bd3202](https://github.com/blu3berry-why/Kraft/commit/0bd3202a29d31339f34606f637e75d8195c777ae))
* **collections:** Map, more collection types and nesting to any depth ([#120](https://github.com/blu3berry-why/Kraft/issues/120)) ([56ebdae](https://github.com/blu3berry-why/Kraft/commit/56ebdae1fba8c5828e3521da7bd470a6bc545f01))
* **diagnostics:** agent-readable diagnostic codes, W001/W002 redundancy warnings ([#118](https://github.com/blu3berry-why/Kraft/issues/118)) ([61ad8f3](https://github.com/blu3berry-why/Kraft/commit/61ad8f39adcb57a9eab3902793a20e7f1ec9eff4))
* flat @MapConfig rules and @MapConstant (roadmap [#40](https://github.com/blu3berry-why/Kraft/issues/40), [#29](https://github.com/blu3berry-why/Kraft/issues/29)) ([#129](https://github.com/blu3berry-why/Kraft/issues/129)) ([910aa30](https://github.com/blu3berry-why/Kraft/commit/910aa307c5fab79fd170255190dfb4c33eee39fc))


### Bug Fixes

* agp9Test fails on Windows (roadmap [#23](https://github.com/blu3berry-why/Kraft/issues/23)) ([#125](https://github.com/blu3berry-why/Kraft/issues/125)) ([7c4e839](https://github.com/blu3berry-why/Kraft/commit/7c4e839928efb813980dcb40243ef37ca94e520e))
* **ci:** let Publish RC cut the first RC of a version ([#119](https://github.com/blu3berry-why/Kraft/issues/119)) ([caf260a](https://github.com/blu3berry-why/Kraft/commit/caf260a56792872171de83765a8b602ff6dac4c2))
* one cause, one error for KRAFT-E015 (roadmap [#24](https://github.com/blu3berry-why/Kraft/issues/24)) ([#126](https://github.com/blu3berry-why/Kraft/issues/126)) ([3032c4e](https://github.com/blu3berry-why/Kraft/commit/3032c4e46152328abc162975b3b0fea932bc78a4))
* order KSP platform tasks after kspCommonMainKotlinMetadata ([#111](https://github.com/blu3berry-why/Kraft/issues/111)) ([760d808](https://github.com/blu3berry-why/Kraft/commit/760d808b7b1f7c2f1d7f371130a314fa11a5fc0b))
* stop generating mapper files with a double .kt extension ([#115](https://github.com/blu3berry-why/Kraft/issues/115)) ([ee1c983](https://github.com/blu3berry-why/Kraft/commit/ee1c983615a5d0b7ddaf80eda34a58844a1fefcf))
* stop silently dropping mappers for defaulted or nullable unmapped targets ([#122](https://github.com/blu3berry-why/Kraft/issues/122)) ([32a29e2](https://github.com/blu3berry-why/Kraft/commit/32a29e214bc7d5a97aa81f70e56eb2c7d48e61a7))

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
