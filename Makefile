all: run

run:
	mvn \
		-Djetty.consoleForceReload=false \
		-Djava.util.logging.config.file=debug-plugin-logging.properties \
		-Dhudson.slaves.NodeProvisioner.initialDelay=0 \
		-Dhudson.slaves.NodeProvisioner.MARGIN=50 \
		-Dhudson.slaves.NodeProvisioner.MARGIN0=0.85 \
		hpi:run

build:
	mvn --batch-mode compile verify package

test:
	mvn clean test jacoco:report
	@echo "Open target/site/jacoco/index.html"

release:
	mvn --batch-mode release:clean release:prepare

clean:
	mvn clean release:clean
