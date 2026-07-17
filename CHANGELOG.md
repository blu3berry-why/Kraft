# Changelog

## [0.12.0](https://github.com/blu3berry-why/Kraft/compare/0.11.0...0.12.0) (2026-07-17)


### ⚠ BREAKING CHANGES

* converter delegates in artifacts built with Kraft <= 0.10.x are no longer discovered, on any platform. Rebuild producing modules on the current Kraft version or declare local converter copies. Behavior change: an ambiguous pair published by two upstream modules is now only an error when a mapper actually needs that pair (lookup is lazy); previously JVM builds failed on unused conflicts too.

### Features

* Add kraft-core module with mapper architecture, enum mapping support, and documentation ([#32](https://github.com/blu3berry-why/Kraft/issues/32)) ([a2c2deb](https://github.com/blu3berry-why/Kraft/commit/a2c2deb1ccf9c1e3c504242d6ae94c14d8402393))
* Add whole-source converter support to @MapUsing   ([#11](https://github.com/blu3berry-why/Kraft/issues/11)) ([6fb4080](https://github.com/blu3berry-why/Kraft/commit/6fb4080ade845c27f792f3b78abb02765e1af763))
* auto-resolve @MapEnum mappers as global converters ([#58](https://github.com/blu3berry-why/Kraft/issues/58)) ([c7efc48](https://github.com/blu3berry-why/Kraft/commit/c7efc48ea2365f61bb2925892a4a6291662479e2))
* **ci:** migrate Maven Central publishing to nmcp and extract convention plugin ([#43](https://github.com/blu3berry-why/Kraft/issues/43)) ([e02330f](https://github.com/blu3berry-why/Kraft/commit/e02330f6d63320d6e88846278734b90f0dbee3f1))
* cross-module @KraftConverter discovery on KMP via by-name delegate resolution ([#84](https://github.com/blu3berry-why/Kraft/issues/84)) ([0b992ce](https://github.com/blu3berry-why/Kraft/commit/0b992ce8bd528098989be70f7e0859469474df6a))
* extend side aliases to @MapEnum mappers ([#70](https://github.com/blu3berry-why/Kraft/issues/70)) ([4a0def1](https://github.com/blu3berry-why/Kraft/commit/4a0def18e5b09a7f8201eef8823cd07c5a1bec83))
* **kraft-ksp:** auto-derive same-module enum mappers when entries pair by name ([#60](https://github.com/blu3berry-why/Kraft/issues/60)) ([7ed584d](https://github.com/blu3berry-why/Kraft/commit/7ed584d44c4eca9113a7aa2a3aa4e5a939fec7a6))
* side aliases — auto-emit toDomain()/toEntity()/toDto() based on package patterns ([#68](https://github.com/blu3berry-why/Kraft/issues/68)) ([f93feed](https://github.com/blu3berry-why/Kraft/commit/f93feed7783753f3e88c6e86395b2adf37c8cc45))
* support @MapReverse on @MapEnum ([#55](https://github.com/blu3berry-why/Kraft/issues/55)) ([93470bd](https://github.com/blu3berry-why/Kraft/commit/93470bd34e2495b651bce9f2307309df188a15bc))
* update release workflow to use release please. ([#20](https://github.com/blu3berry-why/Kraft/issues/20)) ([014fa9b](https://github.com/blu3berry-why/Kraft/commit/014fa9ba3b0b662b0acbb995b5e232f0617da227))


### Bug Fixes

* **ci:** build PR-body changelog from squash-commit bodies ([#53](https://github.com/blu3berry-why/Kraft/issues/53)) ([97a0fc3](https://github.com/blu3berry-why/Kraft/commit/97a0fc30493cb0af90a4beaf8f2e5e4f9085d781))
* **ci:** decode base64 GPG key before import and validate import succeeded ([b010ab8](https://github.com/blu3berry-why/Kraft/commit/b010ab883379e60984ed825f980791ced75af1a4))
* **ci:** ensure gradle.properties newline separator and fix signing/publishing task ordering ([7d93693](https://github.com/blu3berry-why/Kraft/commit/7d93693b4f8e120fc123282b8041e101dccca5e1))
* **ci:** harden credential injection action with shell safety improvements ([071009e](https://github.com/blu3berry-why/Kraft/commit/071009e0e56ccef6cf2cdcc9514b09071a36a664))
* **ci:** pass explicit --notes-start-tag to release-notes generation ([#64](https://github.com/blu3berry-why/Kraft/issues/64)) ([b522e7a](https://github.com/blu3berry-why/Kraft/commit/b522e7a57c86e0b896cbb5187bec87863f51155d))
* **ci:** pass signing key via env vars to avoid shell escaping issues ([#37](https://github.com/blu3berry-why/Kraft/issues/37)) ([2d5cb3f](https://github.com/blu3berry-why/Kraft/commit/2d5cb3ffb17faee2aca8c1b52ecf66af794b3861))
* **ci:** read version from root gradle.properties in publish workflow ([#29](https://github.com/blu3berry-why/Kraft/issues/29)) ([243e850](https://github.com/blu3berry-why/Kraft/commit/243e850ecbd4fed855bb99f1e7b48024a21abaca))
* **ci:** rename release branch prefix to releases/ to avoid git ref conflict ([#24](https://github.com/blu3berry-why/Kraft/issues/24)) ([a66cb52](https://github.com/blu3berry-why/Kraft/commit/a66cb5221c694967ad4806a6962be35d34762187))
* **ci:** rename release branch to release-branch to avoid ref conflict ([#27](https://github.com/blu3berry-why/Kraft/issues/27)) ([3c3c1fe](https://github.com/blu3berry-why/Kraft/commit/3c3c1fe198e2119ec2b41e81dc37af1700f7a69b))
* **ci:** restrict release tag selector to stable MAJOR.MINOR.PATCH ([7b916d3](https://github.com/blu3berry-why/Kraft/commit/7b916d3098fc36e370f93ce7c23a1a259e5f62c5))
* **ci:** scan squash-commit bodies for conventional-commit markers ([00dcdc9](https://github.com/blu3berry-why/Kraft/commit/00dcdc92b6a48537e36de0bef9b327dcb573c21f))
* **ci:** switch docs deploy to GitHub Actions Pages deployment ([#33](https://github.com/blu3berry-why/Kraft/issues/33)) ([b2bf6e6](https://github.com/blu3berry-why/Kraft/commit/b2bf6e6795f5b55f3b75b1125947e26f84038c66))
* **ci:** use correct gradle.properties path in create-release workflow ([#23](https://github.com/blu3berry-why/Kraft/issues/23)) ([dda629b](https://github.com/blu3berry-why/Kraft/commit/dda629bf7ec2ee6e17251c1aa3bff75166addf19))
* **ci:** use gpg cmd for signing to support GPG 2.4+ argon2id keys ([a67bc1b](https://github.com/blu3berry-why/Kraft/commit/a67bc1b9f526f4ed2dbe0a5d3a43dab90a95f6b6))
* **ci:** use gpg cmd for signing to support GPG 2.4+ argon2id keys ([b4c2c4f](https://github.com/blu3berry-why/Kraft/commit/b4c2c4fd349d36eed9a3194bc253d7b031c4f711))
* discover cross-module @KraftConverter delegates on KMP metadata compilations ([#83](https://github.com/blu3berry-why/Kraft/issues/83)) ([7ef9974](https://github.com/blu3berry-why/Kraft/commit/7ef9974ecf2cbadf12c9fa4158d2daa0a162006c))
* **kraft-core:** resolve through type aliases when analyzing types ([#74](https://github.com/blu3berry-why/Kraft/issues/74)) ([2d5963c](https://github.com/blu3berry-why/Kraft/commit/2d5963c6b09570c8120c63ef5418cee056004a15))
* **kraft-ksp:** disambiguate generated mapper filenames when nested simple names collide ([#65](https://github.com/blu3berry-why/Kraft/issues/65)) ([c755f2d](https://github.com/blu3berry-why/Kraft/commit/c755f2dd2c741473d5840582c1fa066d397fb816))
* **kraft-ksp:** emit imports for nested mappers in different packages ([#62](https://github.com/blu3berry-why/Kraft/issues/62)) ([cf33c3c](https://github.com/blu3berry-why/Kraft/commit/cf33c3c7f3779367548e1efd03ceade32753da2c))
* qualify nested types in generated mapper imports ([#57](https://github.com/blu3berry-why/Kraft/issues/57)) ([874fa04](https://github.com/blu3berry-why/Kraft/commit/874fa042ac45ac55bc33aa8e2010ab40434f876e))
* update ci to create pr ([#26](https://github.com/blu3berry-why/Kraft/issues/26)) ([ed99021](https://github.com/blu3berry-why/Kraft/commit/ed9902156cc1e83f55b34225789fb6453c426dde))
