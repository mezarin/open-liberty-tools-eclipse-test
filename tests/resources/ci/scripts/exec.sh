#!/bin/bash

############################################################################
# Copyright (c) 2022, 2023 IBM Corporation and others.
# 
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0.
# 
# SPDX-License-Identifier: EPL-2.0
# 
# Contributors:
#     IBM Corporation - initial implementation
############################################################################
set -Ex

# Current time.
currentTime=(date +"%Y/%m/%d-%H:%M:%S:%3N")

# Operating system.
OS=$(uname -s)

main() {

    TARGET="$1"

    echo "LTE test begin for target platform = $TARGET"

    echo -e "\n> $(${currentTime[@]}): Build: Building the plugin"

    # Tell the terminal session to use display port 77.
    export DISPLAY=:77.0

    # Install software.
    if [ $OS = "Linux" ]; then
        # Start the X display server on port 77.
        Xvfb -ac :77 -screen 0 1280x1024x16 > /dev/null 2>&1 &

        #  Start the window manager.
        metacity --sm-disable --replace 2> metacity.err &
    fi
    which gradle
    gradle -v
    echo "@ed! what is in cache .. "
    ls -l Users/runner/.gradle/
    ls -l Users/runner/.gradle/caches/*
     echo "@ed! removing cache .. "
    rm -rf Users/runner/.gradle/caches
    
    mvn clean install -Declipse.target="$TARGET" -Dosgi.debug=./tests/resources/ci/debug.opts -Dtycho.showEclipseLog -DtestAppImportWait=120000 -DmvnPath="${PWD}/test-tools/liberty-dev-tools/apache-maven-3.9.6" -DgradlePath="${PWD}/test-tools/liberty-dev-tools/gradle-8.8" -DmvnLogFile

    # If there were any errors, gather some debug data before exiting.
    rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "ERROR: Failure while driving mvn install on plugin. rc: ${rc}."

        echo "DEBUG: Liberty messages.log:\n"
        cat tests/resources/applications/maven/liberty-maven-test-app/target/liberty/wlp/usr/servers/defaultServer/logs/messages.log

        echo "DEBUG: Environment variables:\n"
        env

        exit -1
    fi
}

main "$@"
