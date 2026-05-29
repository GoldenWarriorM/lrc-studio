#!/bin/sh

# Gradle wrapper - downloads the wrapper jar if missing
set -e

APP_HOME=$( cd "${0%[/\\]*}" > /dev/null && pwd -P ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
    echo "Downloading Gradle wrapper JAR..."
    GRADLE_VERSION=$(grep distributionUrl "$APP_HOME/gradle/wrapper/gradle-wrapper.properties" | sed 's/.*gradle-\(.*\)-bin.zip/\1/')
    WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v$GRADLE_VERSION/gradle/wrapper/gradle-wrapper.jar"
    if command -v curl >/dev/null 2>&1; then
        curl -sS -o "$CLASSPATH" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-wrapper.jar?event=download"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$CLASSPATH" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-wrapper.jar?event=download"
    else
        echo "ERROR: Cannot download Gradle wrapper. Install curl or wget."
        exit 1
    fi
    if [ ! -f "$CLASSPATH" ] || [ ! -s "$CLASSPATH" ]; then
        echo "ERROR: Failed to download Gradle wrapper JAR"
        exit 1
    fi
    echo "Gradle wrapper JAR downloaded."
fi

# Use JAVA_HOME if set, otherwise use java from PATH
if [ -n "$JAVA_HOME" ]; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: Java not found. Install JDK 17+ and set JAVA_HOME."
    exit 1
fi

exec "$JAVACMD" \
    -Dorg.gradle.appname=gradlew \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
