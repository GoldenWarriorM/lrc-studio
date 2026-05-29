#!/bin/bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.5-tem
./gradlew :composeApp:assembleDebug "$@"
