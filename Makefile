.DEFAULT_GOAL := help

help:
	@echo "\033[33mUsage:\033[0m\n  make [target] [arg=\"val\"...]\n\n\033[33mTargets:\033[0m"
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(firstword $(MAKEFILE_LIST)) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[32m%-25s\033[0m %s\n", $$1, $$2}'

gradlew-build: ## Build gradle
	./gradlew build

test: ## Running tests
	./gradlew test

gradlew-tasks:
	./gradlew tasks

detekt: ## Running with type resolution
	${MAKE} detekt-main
	${MAKE} detekt-test

detekt-test: ## Running tests with type resolution
	./gradlew detektTest

detekt-main: ## Running main with type resolution
	./gradlew detektMain

detekt-baseline-main: ## Creating/updating baseline for main
	./gradlew detektBaselineMain

detekt-baseline-test: ## Creating/updating baseline for test
	./gradlew detektBaselineTest

#=======Aliases=======

t: test ## Alias for "test".

gb: gradlew-build ## Alias for "gradlew build".

gt: gradlew-tasks ## Alias for "gradlew tasks".

d: detekt ## Alias for "detekt".

dm: detekt-main ## Alias for "detekt-main".

dt: detekt-test ## Alias for "detekt-test".

dbm: detekt-baseline-main ## Alias for "detekt-baseline-main".

dbt: detekt-baseline-test ## Alias for "detekt-baseline-test".
