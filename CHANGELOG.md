# Changelog

## [5.1.0](https://github.com/google/quota-monitoring-solution/compare/v5.0.2...v5.1.0) (2023-09-21)


### Features

* Add user-agent string to the terraform module. ([#95](https://github.com/google/quota-monitoring-solution/issues/95)) ([87780ec](https://github.com/google/quota-monitoring-solution/commit/87780ecdfce90217e1b822891c30e2b7722734bb))


### Bug Fixes

* adding main.tf to the extra files for release-please ([#96](https://github.com/google/quota-monitoring-solution/issues/96)) ([0c48faa](https://github.com/google/quota-monitoring-solution/commit/0c48faa13f185e00329443472cfbab696a563bec))
* Fix for Issue [#86](https://github.com/google/quota-monitoring-solution/issues/86) (per second quotas) ([#89](https://github.com/google/quota-monitoring-solution/issues/89)) ([c2e5d47](https://github.com/google/quota-monitoring-solution/commit/c2e5d477574bfed4e77be2b6369a1f8d1b94a9ea))
* Removing the leading pipe from the log filter. ([#90](https://github.com/google/quota-monitoring-solution/issues/90)) ([eab522e](https://github.com/google/quota-monitoring-solution/commit/eab522e9620e9fefdb31639f5ea656b0f10a1cd5))
* Updating the README to clarify the preferred paths to get support. ([#93](https://github.com/google/quota-monitoring-solution/issues/93)) ([dcdeafb](https://github.com/google/quota-monitoring-solution/commit/dcdeafbfbc578f28e182eff59ab827e5c6d4853d))

## [5.0.2](https://github.com/google/quota-monitoring-solution/compare/v5.0.1...v5.0.2) (2023-05-22)


### Bug Fixes

* Add quotes to BQ table location ([#84](https://github.com/google/quota-monitoring-solution/issues/84)) ([dcd52c3](https://github.com/google/quota-monitoring-solution/commit/dcd52c30918a5293a07c163158f24faa4456c216))

## [5.0.1](https://github.com/google/quota-monitoring-solution/compare/v5.0.0...v5.0.1) (2023-04-26)


### Bug Fixes

* Feature/looker studio template public ([#80](https://github.com/google/quota-monitoring-solution/issues/80)) ([4d423e3](https://github.com/google/quota-monitoring-solution/commit/4d423e32b80af4061908704bb8fb89458fabbbbd))
* Implemented [#67](https://github.com/google/quota-monitoring-solution/issues/67) - use short-lived tokens ([#69](https://github.com/google/quota-monitoring-solution/issues/69)) ([d08475a](https://github.com/google/quota-monitoring-solution/commit/d08475a622b82d21cb125a35571720fb69fe53d3))

## [5.0.0](https://github.com/google/quota-monitoring-solution/compare/v4.5.1...v5.0.0) (2023-01-18)


### ⚠ BREAKING CHANGES

* App Level Alerting along with centralized alerting  ([#61](https://github.com/google/quota-monitoring-solution/issues/61))

### Features

* App Level Alerting along with centralized alerting  ([#61](https://github.com/google/quota-monitoring-solution/issues/61)) ([339a8b6](https://github.com/google/quota-monitoring-solution/commit/339a8b6972f085e944c7b225b1bfc1df0af1f5ff))

## [4.5.1](https://github.com/google/quota-monitoring-solution/compare/v4.5.0...v4.5.1) (2022-12-30)


### Bug Fixes

* removed redundant code ([415ffbf](https://github.com/google/quota-monitoring-solution/commit/415ffbf33d8c5f9848ed21fe25ec96c6ed3a5d46))

## [4.5.0](https://github.com/google/quota-monitoring-solution/compare/v4.4.0...v4.5.0) (2022-12-23)


### Features

* Alerting notification in html ([13f5754](https://github.com/google/quota-monitoring-solution/commit/13f5754e07c50e9e77d2640b6f29bddbd25bcc80))


### Bug Fixes

* Alerting fixes ([1a11ce8](https://github.com/google/quota-monitoring-solution/commit/1a11ce8393cb187a0db46ed14709a45988066272))

## [4.4.0](https://github.com/google/quota-monitoring-solution/compare/v4.3.0...v4.4.0) (2022-12-22)


### Features

* Adding release-please to manage version numbers and updating Terraform to pull from GitHub releases. ([56b42a5](https://github.com/google/quota-monitoring-solution/commit/56b42a5b3c5fb4d676e39f1caafe53fa27bd51a7))
* updating the README to include upgrade steps for Issue 18 ([5c5a784](https://github.com/google/quota-monitoring-solution/commit/5c5a784ce118cf7fb82e80ec60ab51eee792d8c4))


### Bug Fixes

* adding end of line to fix linter error. ([7293c18](https://github.com/google/quota-monitoring-solution/commit/7293c18ad4909171d239463eea1d26f4b43f5565))
* correcting the path for GitHub workflows. ([4baf80a](https://github.com/google/quota-monitoring-solution/commit/4baf80ac121ef2a7c39c07465cc17dcd35a536d9))
* do not lint CHANGELOG.md ([e8959a5](https://github.com/google/quota-monitoring-solution/commit/e8959a5a89bba6cb0b1b37d71b04dc2eea453af2))
* removing old markdown linter config files. ([00fc7f4](https://github.com/google/quota-monitoring-solution/commit/00fc7f4694c0ff9a037c769c35ae38e64dce1cbe))
* resolving some missing dependencies between resources. ([be13e64](https://github.com/google/quota-monitoring-solution/commit/be13e64bc92d105afc291a86c36c5abcbe4e79cb))
* updating release workflow to use a different token and pinning versions. ([e68a9d0](https://github.com/google/quota-monitoring-solution/commit/e68a9d05273bbf3fe24dac2e9f86d8f03420ab28))
* updating the markdown linter to use the GitHub super linter. ([e5b2a4f](https://github.com/google/quota-monitoring-solution/commit/e5b2a4fc32151f915726b4918e19916bffd12b22))
