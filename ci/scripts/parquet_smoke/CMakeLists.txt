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

cmake_minimum_required(VERSION 3.18)
project(arrow_parquet_package_smoke LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Arrow CONFIG REQUIRED)
find_package(ArrowCompute CONFIG REQUIRED)
find_package(Parquet CONFIG REQUIRED)
foreach(codec SNAPPY ZLIB ZSTD)
  if(NOT ARROW_WITH_${codec})
    message(FATAL_ERROR "Missing ${codec} in packaged Arrow")
  endif()
endforeach()
add_executable(parquet_smoke parquet_smoke.cc)
target_link_libraries(parquet_smoke
                      PRIVATE Parquet::parquet_static ArrowCompute::arrow_compute_static
                              Arrow::arrow_static)
add_library(parquet_shared_smoke SHARED parquet_smoke.cc)
target_link_libraries(parquet_shared_smoke
                      PRIVATE Parquet::parquet_static ArrowCompute::arrow_compute_static
                              Arrow::arrow_static)
enable_testing()
add_test(NAME parquet_codec_round_trip COMMAND parquet_smoke)
