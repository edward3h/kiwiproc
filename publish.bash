#!/bin/bash

set -e

if [ $(git status --porcelain=1 | wc -l) -ne 0 ]; then
  echo "There are local modifications. Resolve before publishing."
  exit 1
fi

if grep SNAPSHOT version.gradle.kts; then
  echo "Set a non-snapshot version, then run this script."
  exit 2
fi

# publish plugin library to maven
pushd gradle-plugin/
gw clean
gw build
gw publishAllPublicationsToProjectLocalRepository zipMavenCentralPortalPublication releaseMavenCentralPortalPublication
popd
# publish main libraries to maven
gw clean
gw build

gw publishAllPublicationsToProjectLocalRepository zipMavenCentralPortalPublication releaseMavenCentralPortalPublication
# publish gradle plugin
pushd gradle-plugin/
gw publishPlugins
popd
# publish docs
gh workflow run docs.yml

echo "Publish complete. Change version back to snapshot."
