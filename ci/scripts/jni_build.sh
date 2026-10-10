#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

set -euo pipefail

# shellcheck source=ci/scripts/util_log.sh
. "$(dirname "${0}")/util_log.sh"

github_actions_group_begin "Prepare arguments"
source_dir=${1}
arrow_install_dir=${2}
build_dir=${3}/java_jni
# The directory where the final binaries will be stored when scripts finish
dist_dir=${4}
prefix_dir="${build_dir}/java-jni"
github_actions_group_end

github_actions_group_begin "Clear output directories and leftovers"
rm -rf "${build_dir}"
github_actions_group_end

github_actions_group_begin "Building Arrow Java C Data Interface native library"

case "$(uname)" in
Linux)
  n_jobs=$(nproc)
  ;;
Darwin)
  n_jobs=$(sysctl -n hw.logicalcpu)
  ;;
*)
  n_jobs=${NPROC:-1}
  ;;
esac

: "${ARROW_JAVA_BUILD_TESTS:=${ARROW_BUILD_TESTS:-ON}}"
: "${CMAKE_BUILD_TYPE:=release}"
read -ra EXTRA_CMAKE_OPTIONS <<<"${JAVA_JNI_CMAKE_ARGS:-}"
cmake \
  -S "${source_dir}" \
  -B "${build_dir}" \
  -DARROW_JAVA_JNI_ENABLE_DATASET="${ARROW_DATASET:-OFF}" \
  -DARROW_JAVA_JNI_ENABLE_GANDIVA="${ARROW_GANDIVA:-OFF}" \
  -DARROW_JAVA_JNI_ENABLE_ORC="${ARROW_ORC:-OFF}" \
  -DBUILD_TESTING="${ARROW_JAVA_BUILD_TESTS}" \
  -DCMAKE_BUILD_TYPE="${CMAKE_BUILD_TYPE}" \
  -DCMAKE_PREFIX_PATH="${arrow_install_dir}" \
  -DCMAKE_INSTALL_PREFIX="${prefix_dir}" \
  -DCMAKE_UNITY_BUILD="${CMAKE_UNITY_BUILD:-OFF}" \
  -DProtobuf_USE_STATIC_LIBS=ON \
  -GNinja \
  "${EXTRA_CMAKE_OPTIONS[@]}"
cmake --build "${build_dir}" --verbose
if [ "${ARROW_JAVA_BUILD_TESTS}" = "ON" ]; then
  ctest \
    --output-on-failure \
    --parallel "${n_jobs}" \
    --test-dir "${build_dir}" \
    --timeout 300
fi
cmake --build "${build_dir}" --target install

github_actions_group_end

github_actions_group_begin "Copying artifacts"
mkdir -p "${dist_dir}"
# For Windows. *.dll are installed into bin/ on Windows.
if [ -d "${prefix_dir}/bin" ]; then
  mv "${prefix_dir}"/bin/* "${dist_dir}"/
else
  mv "${prefix_dir}"/lib/* "${dist_dir}"/
fi
github_actions_group_end

if [ -n "${5:-}" ]; then
  github_actions_group_begin "Stage Arrow Parquet release libraries"
  case "$(uname)" in
  Linux) os=linux ;;
  Darwin) os=osx ;;
  esac
  arch=$(uname -m)
  case "${arch}" in
  arm64 | aarch64) arch=aarch_64 ;;
  esac
  package_dir="${dist_dir}/arrow_parquet/${os}-${arch}"
  mkdir -p "${package_dir}/lib/pkgconfig" "${package_dir}/META-INF"
  cp -R "${arrow_install_dir}/include" "${package_dir}/"
  cp "${5}/LICENSE.txt" "${package_dir}/META-INF/LICENSE"
  cp "${5}/NOTICE.txt" "${package_dir}/META-INF/NOTICE"
  git -c safe.directory="${5}" -C "${5}" rev-parse HEAD >"${package_dir}/ARROW_CPP_REVISION"
  parquet_metadata=$(find "${arrow_install_dir}" -type f -name parquet.pc -print -quit)
  if [ -z "${parquet_metadata}" ]; then
    echo "Missing installed Parquet pkg-config metadata" >&2
    exit 1
  fi
  PKG_CONFIG_PATH="$(dirname "${parquet_metadata}"):${6:+${6}:}${PKG_CONFIG_PATH:-}"
  export PKG_CONFIG_PATH
  packages=(parquet arrow-compute)
  for dependency in re2 thrift liblz4 libzstd libutf8proc; do
    if pkg-config --exists "${dependency}"; then
      packages+=("${dependency}")
    fi
  done
  library_flags=$(pkg-config --static --libs-only-L "${packages[@]}")
  read -ra library_dirs <<<"${library_flags}"
  library_flags=$(pkg-config --static --libs "${packages[@]}")
  read -ra libraries <<<"${library_flags}"
  cpp_version=$(pkg-config --modversion parquet)
  link_flags=()
  for flag in "${libraries[@]}"; do
    case "${flag}" in
    -L*) continue ;;
    -lc | -lm | -ldl | -lpthread | -lrt | -lstdc++ | -lc++ | -lresolv)
      link_flags+=("${flag}")
      continue
      ;;
    -l*)
      archive=""
      for directory in "${library_dirs[@]}"; do
        candidate="${directory#-L}/lib${flag#-l}.a"
        if [ -f "${candidate}" ]; then
          archive="${candidate}"
          break
        fi
      done
      if [ -z "${archive}" ]; then
        archive=$(c++ -print-file-name="lib${flag#-l}.a")
      fi
      if [ ! -f "${archive}" ]; then
        if [ "${os}" = osx ] && [[ "${flag}" = -lz || "${flag}" = -lcurl ]]; then
          link_flags+=("${flag}")
          continue
        fi
        echo "Missing static dependency: ${flag}" >&2
        exit 1
      fi
      cp "${archive}" "${package_dir}/lib/"
      ;;
    *.a)
      cp "${flag}" "${package_dir}/lib/"
      archive_name=$(basename "${flag}" .a)
      flag="-l${archive_name#lib}"
      ;;
    esac
    link_flags+=("${flag}")
  done
  cat >"${package_dir}/lib/pkgconfig/arrow-parquet.pc" <<EOF
prefix=\${pcfiledir}/../..
libdir=\${prefix}/lib
includedir=\${prefix}/include
Name: Arrow Parquet
Description: Static Arrow C++ Parquet libraries (requires C++20 for Arrow 23)
Version: ${cpp_version}
Libs: -L\${libdir} ${link_flags[*]}
Cflags: -I\${includedir} -DARROW_STATIC -DARROW_COMPUTE_STATIC -DPARQUET_STATIC
EOF
  github_actions_group_begin "Verify staged Arrow Parquet SDK"
  cat >"${build_dir}/parquet_sdk_smoke.cc" <<'EOF'
#include <arrow/api.h>
#include <arrow/compute/initialize.h>
#include <arrow/io/api.h>
#include <parquet/arrow/reader.h>
#include <parquet/arrow/writer.h>

int main() {
  if (!arrow::compute::Initialize().ok()) return 1;
  arrow::Int64Builder builder;
  if (!builder.Append(42).ok()) return 1;
  auto values = builder.Finish().ValueOrDie();
  auto table = arrow::Table::Make(arrow::schema({arrow::field("value", arrow::int64())}), {values});
  for (auto codec : {parquet::Compression::SNAPPY, parquet::Compression::GZIP,
                     parquet::Compression::ZSTD}) {
    auto output = arrow::io::BufferOutputStream::Create().ValueOrDie();
    auto properties = parquet::WriterProperties::Builder().compression(codec)->build();
    if (!parquet::arrow::WriteTable(*table, arrow::default_memory_pool(), output, 1,
                                    properties).ok()) return 1;
    auto input = std::make_shared<arrow::io::BufferReader>(output->Finish().ValueOrDie());
    auto reader = parquet::arrow::OpenFile(input, arrow::default_memory_pool()).ValueOrDie();
    std::shared_ptr<arrow::Table> actual;
    if (!reader->ReadTable(&actual).ok() || !table->Equals(*actual)) return 1;
  }
}
EOF
  smoke_flags=$(PKG_CONFIG_PATH='' PKG_CONFIG_LIBDIR="${package_dir}/lib/pkgconfig" pkg-config --cflags --libs arrow-parquet)
  read -ra smoke_options <<<"${smoke_flags}"
  "${CXX:-c++}" -std=c++20 "${build_dir}/parquet_sdk_smoke.cc" "${smoke_options[@]}" -o "${build_dir}/parquet_sdk_smoke"
  "${build_dir}/parquet_sdk_smoke"
  github_actions_group_end
  github_actions_group_end
fi
