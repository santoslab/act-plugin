#!/usr/bin/env bash
set -e
: ${ACT_HOME:=$( cd "$( dirname "$0" )"/.. &> /dev/null && pwd )}
BIN_DIR=${ACT_HOME}/bin
ZULU_VERSION=10.3+5-jdk10.0.2
MILL_URL=http://files.sireum.org/mill-standalone # see https://github.com/sireum/mill-build
MILL_SHA3=4e4d53f3341ce5b57e1a838b56876a339bac73bc421fa353a982018dc3c72a86
MILL=${BIN_DIR}/mill
LIB_URL=https://raw.githubusercontent.com/sireum/kekinian/c9d323bc61ad1cbc675469f6d9c87079c21abce7/versions.properties
LIB_SHA3=5cec55e8939563aac4c8e6f61917c01ff98fb9f1e476d8c3cefe1a3f0eeeef20
LIB=${ACT_HOME}/versions.properties
SIREUM_URL=http://files.sireum.org/sireum # see https://github.com/sireum/kekinian
SIREUM_SHA3=4951b8f1fb4c175dead613a005b9863f4365ab77f1885771220f8b5c1cb71fbf
if [ -z "${PLATFORM}" ]; then
  if [ -n "$COMSPEC" -a -x "$COMSPEC" ]; then
    PLATFORM=win
  elif [ "$(uname)" == "Darwin" ]; then
    PLATFORM=mac
  elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
    PLATFORM=linux
  else
    >&2 echo "Unsupported platform to build ACT."
    exit 1
  fi
fi
if [ "${PLATFORM}" = "win" ]; then
  ZULU_URL=http://cdn.azul.com/zulu/bin/zulu${ZULU_VERSION}-win_x64.zip
  SHA3=${BIN_DIR}/win/sha3.exe
  SHA3_URL=http://files.sireum.org/win/sha3.exe
  SIREUM=${BIN_DIR}/sireum.bat
elif [ "${PLATFORM}" = "mac" ]; then
  ZULU_URL=http://cdn.azul.com/zulu/bin/zulu${ZULU_VERSION}-macosx_x64.zip
  SHA3=${BIN_DIR}/mac/sha3
  SHA3_URL=http://files.sireum.org/mac/sha3
  SIREUM=${BIN_DIR}/sireum
elif [ "${PLATFORM}" = "linux" ]; then
  ZULU_URL=http://cdn.azul.com/zulu/bin/zulu${ZULU_VERSION}-linux_x64.tar.gz
  SHA3=${BIN_DIR}/linux/sha3
  SHA3_URL=http://files.sireum.org/linux/sha3
  SIREUM=${BIN_DIR}/sireum
fi
mkdir -p ${BIN_DIR}/${PLATFORM}
cd ${BIN_DIR}/${PLATFORM}
ZULU="${ZULU_URL##*/}"
ZULU_DIR="${ZULU%.*}"
if [[ ${ZULU_DIR} == *.tar ]]; then
  ZULU_DIR="${ZULU_DIR%.*}"
fi
grep -q ${ZULU_VERSION} jdk/VER &> /dev/null && ZULU_UPDATE=false || ZULU_UPDATE=true
if [ ! -d jdk ] || [ "${ZULU_UPDATE}" = "true" ]; then
  if [ ! -f ${ZULU} ]; then
    if [ -f ${SIREUM_CACHE}/${ZULU} ]; then
      echo "Using ${SIREUM_CACHE}/${ZULU} ... "
      ln -s ${SIREUM_CACHE}/${ZULU}
      echo
    else
      echo "Downloading Zulu JDK ${ZULU_VERSION} ..."
      curl -Lo ${ZULU} ${ZULU_URL}
      echo
      if [ ! -z ${SIREUM_CACHE} ]; then
        echo "Copying to ${SIREUM_CACHE}/${ZULU} ..."
        cp ${ZULU} ${SIREUM_CACHE}/${ZULU}
        echo
      fi
    fi
  fi
  if [[ ${ZULU} == *.zip ]]; then
    unzip -oq ${ZULU}
  else
    tar xf ${ZULU}
  fi
  rm ${ZULU}
  rm -fR jdk
  mv ${ZULU_DIR} jdk
  if [ -d "jdk/bin" ]; then
    echo "${ZULU_VERSION}" > jdk/VER
  else
    >&2 echo "Could not install Zulu JDK ${ZULU_VERSION}."
    exit 1
  fi
fi
if [ ! -f ${SHA3} ]; then
  echo "Downloading sha3 ..."
  curl -Lo ${SHA3} ${SHA3_URL}
  chmod +x ${SHA3}
fi
if [ ! -f ${MILL} ] || [ "$(${SHA3} 256 < ${MILL})" != ${MILL_SHA3} ]; then
  echo "Downloading mill ..."
  curl -Lo ${MILL} ${MILL_URL}
  chmod +x ${MILL}
  MILL_SHA3_LOCAL="$(${SHA3} 256 < ${MILL})"
  if [ "$(${SHA3} 256 < ${MILL})" != ${MILL_SHA3} ]; then
    >&2 echo "Mill version mismatch (${MILL_SHA3_LOCAL} != ${MILL_SHA3}); please notify ACT maintainers."
  fi
fi
if [ ! -f ${LIB} ] || [ "$(${SHA3} 256 < ${LIB})" != ${LIB_SHA3} ]; then
  echo "Downloading versions.properties ..."
  curl -Lo ${LIB} ${LIB_URL}
  LIB_SHA3_LOCAL="$(${SHA3} 256 < ${LIB})"
  if [ ${LIB_SHA3_LOCAL} != ${LIB_SHA3} ]; then
    >&2 echo "Library dependency versions mismatch (${LIB_SHA3_LOCAL} != ${LIB_SHA3}); please notify ACT maintainers."
  fi
fi
if [ ! -f ${SIREUM} ] || [ "$(${SHA3} 256 < ${SIREUM})" != ${SIREUM_SHA3} ]; then
  echo "Downloading sireum ..."
  curl -Lo ${SIREUM} ${SIREUM_URL}
  chmod +x ${SIREUM}
  SIREUM_SHA3_LOCAL="$(${SHA3} 256 < ${SIREUM})"
  if [ ${SIREUM_SHA3_LOCAL} != ${SIREUM_SHA3} ]; then
    >&2 echo "Sireum version mismatch (${SIREUM_SHA3_LOCAL} != ${SIREUM_SHA3}); please notify ACT maintainers."
  fi
fi