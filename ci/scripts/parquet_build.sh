#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

set -euo pipefail

source_dir="$(cd "${1}" && pwd)"
arrow_dir="$(cd "${2}" && pwd)"
build_dir="${3}/parquet"
mkdir -p "${4}"
dist_dir="$(cd "${4}" && pwd)/arrow_parquet"
mkdir -p "${build_dir}"
build_dir="$(cd "${build_dir}" && pwd)"

cmake -S "${arrow_dir}/cpp" -B "${build_dir}/cpp" -GNinja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="${build_dir}/install" \
  -DCMAKE_INSTALL_LIBDIR=lib \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DARROW_BUILD_STATIC=ON -DARROW_BUILD_SHARED=OFF \
  -DARROW_COMPUTE=ON -DARROW_PARQUET=ON \
  -DARROW_WITH_SNAPPY=ON -DARROW_WITH_ZLIB=ON -DARROW_WITH_ZSTD=ON \
  -DARROW_DEPENDENCY_SOURCE=BUNDLED -DARROW_DEPENDENCY_USE_SHARED=OFF \
  -DARROW_BUILD_TESTS=OFF -DARROW_BUILD_BENCHMARKS=OFF \
  -DARROW_ACERO=OFF -DARROW_DATASET=OFF -DARROW_GANDIVA=OFF \
  -DARROW_CSV=OFF -DARROW_JSON=OFF -DARROW_ORC=OFF \
  -DARROW_S3=OFF -DARROW_GCS=OFF -DARROW_AZURE=OFF -DARROW_SUBSTRAIT=OFF \
  -DARROW_JEMALLOC=OFF -DARROW_MIMALLOC=OFF \
  -DARROW_USE_CCACHE="${ARROW_USE_CCACHE:-ON}" -DARROW_WITH_BACKTRACE=OFF
cmake --build "${build_dir}/cpp" --target install --parallel "${ARROW_PARQUET_BUILD_JOBS:-4}"

package_dir=$(python3 "${source_dir}/ci/scripts/package_arrow_parquet.py" \
  stage "${arrow_dir}" "${build_dir}/install" "${dist_dir}")
mv "${build_dir}/install" "${build_dir}/install-hidden"
trap 'mv "${build_dir}/install-hidden" "${build_dir}/install"' EXIT
cmake -S "${source_dir}/ci/scripts/parquet_smoke" -B "${build_dir}/smoke" -GNinja \
  -DCMAKE_PREFIX_PATH="${package_dir}" \
  -DCMAKE_FIND_USE_PACKAGE_REGISTRY=OFF \
  -DCMAKE_FIND_USE_SYSTEM_PACKAGE_REGISTRY=OFF
cmake --build "${build_dir}/smoke" --parallel 2
ctest --test-dir "${build_dir}/smoke" --output-on-failure
