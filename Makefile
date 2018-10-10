all: run

run:
	mvn hpi:run -Djetty.consoleForceReload=false -Djava.util.logging.config.file=debug-plugin-logging.properties

test:
	mvn clean test jacoco:report
	@echo "Open target/site/jacoco/index.html"
