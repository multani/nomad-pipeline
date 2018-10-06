all: run

run:
	mvn hpi:run -Djetty.consoleForceReload=false -Djava.util.logging.config.file=debug-plugin-logging.properties
