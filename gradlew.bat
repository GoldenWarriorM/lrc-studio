@rem Gradle wrapper script for Windows
@if "%DEBUG%"=="" @echo off
setlocal enabledelayedexpansion

set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
    echo Downloading Gradle wrapper JAR...
    powershell -Command "& { Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.9-wrapper.jar?event=download' -OutFile '%CLASSPATH%' }"
    if errorlevel 1 (
        echo ERROR: Failed to download Gradle wrapper JAR
        exit /b 1
    )
    echo Gradle wrapper JAR downloaded.
)

if "%JAVA_HOME%"=="" (
    set JAVA_CMD=java
) else (
    set JAVA_CMD=%JAVA_HOME%\bin\java
)

"%JAVA_CMD%" -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
if errorlevel 1 exit /b 1

endlocal
