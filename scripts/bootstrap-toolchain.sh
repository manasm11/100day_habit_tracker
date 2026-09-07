#!/usr/bin/env bash
set -euo pipefail

PREFIX="${HOME}/.local"
JDK_DIR="${PREFIX}/jdk17"
SDK_DIR="${PREFIX}/android-sdk"
GRADLE_DIR="${PREFIX}/gradle"
GRADLE_VERSION="8.9"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

mkdir -p "${PREFIX}" "${SDK_DIR}"

if [ ! -x "${JDK_DIR}/bin/javac" ]; then
  echo ">> Downloading Temurin JDK 17"
  curl -fsSL -o /tmp/jdk17.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  rm -rf "${JDK_DIR}" && mkdir -p "${JDK_DIR}"
  tar -xzf /tmp/jdk17.tar.gz -C "${JDK_DIR}" --strip-components=1
fi

if [ ! -x "${GRADLE_DIR}/bin/gradle" ]; then
  echo ">> Downloading Gradle ${GRADLE_VERSION}"
  curl -fsSL -o /tmp/gradle.zip \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  rm -rf "${GRADLE_DIR}" /tmp/gradle-x && mkdir -p /tmp/gradle-x
  unzip -qo /tmp/gradle.zip -d /tmp/gradle-x
  mv "/tmp/gradle-x/gradle-${GRADLE_VERSION}" "${GRADLE_DIR}"
fi

export JAVA_HOME="${JDK_DIR}"
export PATH="${JAVA_HOME}/bin:${PATH}"

if [ ! -d "${SDK_DIR}/cmdline-tools/latest" ]; then
  echo ">> Downloading Android command-line tools"
  curl -fsSL -o /tmp/cmdline-tools.zip \
    "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
  rm -rf /tmp/cmdline-tools-x && mkdir -p /tmp/cmdline-tools-x
  unzip -qo /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-x
  mkdir -p "${SDK_DIR}/cmdline-tools"
  mv /tmp/cmdline-tools-x/cmdline-tools "${SDK_DIR}/cmdline-tools/latest"
fi

export ANDROID_HOME="${SDK_DIR}"
SDKMANAGER="${SDK_DIR}/cmdline-tools/latest/bin/sdkmanager"

echo ">> Accepting licenses"
yes | "${SDKMANAGER}" --sdk_root="${SDK_DIR}" --licenses >/dev/null || true

echo ">> Installing platform-tools, platforms;android-35, build-tools;35.0.0"
"${SDKMANAGER}" --sdk_root="${SDK_DIR}" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"

cat > "${PWD}/scripts/env.sh" <<EOF
export JAVA_HOME="${JDK_DIR}"
export ANDROID_HOME="${SDK_DIR}"
export PATH="\${JAVA_HOME}/bin:${GRADLE_DIR}/bin:\${PATH}"
EOF

rm -f /tmp/jdk17.tar.gz /tmp/gradle.zip /tmp/cmdline-tools.zip

echo ">> Done. Run: source scripts/env.sh"
