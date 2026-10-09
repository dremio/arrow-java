<!---
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements. See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership. The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied. See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# Arrow Parquet release artifact

`jarbuild.yml` publishes `org.apache.arrow:arrow-parquet` alongside the existing
Arrow Java artifacts. It uses the same Maven version as those artifacts and the
Arrow C++ checkout selected by the workflow inputs. No Arrow APIs are changed.

The primary JAR contains release metadata. Native development files are in JARs
with the classifiers `linux-x86_64`, `linux-aarch_64`, and `osx-aarch_64`. Each
classified JAR contains:

- `include/`: Arrow and Parquet headers.
- `lib/`: position-independent static Arrow, Arrow Compute, Parquet, and bundled
  dependency libraries.
- `lib/cmake/`: installed CMake package definitions.
- `share/licenses/arrow/`: Arrow C++ license and notice.
- `manifest.json`: the full Arrow C++ revision, version, platform, codecs,
  library build standard, and checksums of every packaged file.

Native jobs build a separate minimal static installation with Snappy, Gzip, and
Zstd enabled and bundled dependencies. This avoids carrying Gandiva's LLVM or
Dataset's cloud storage dependencies into the Parquet writer. The source remains
the same Arrow C++ revision used by the JNI build.

Arrow C++ 23 and its exported CMake targets require C++20; Parquet's public
metadata headers use `std::span`. Before packaging, a separate C++20 consumer
links both an executable and a shared library against the staged installation;
the executable round-trips nullable data through each codec.
The original installation is hidden during this check to verify relocation.

Consumers unpack the appropriate classified JAR and set `CMAKE_PREFIX_PATH` to
its root. Link `Parquet::parquet_static`, `ArrowCompute::arrow_compute_static`,
and `Arrow::arrow_static`. Maven can unpack the classified dependency; Bazel can
extract the same JAR and expose the archives through `cc_import`.

The aggregation step rejects missing platforms, mixed C++ revisions, changed
contents, or missing required libraries before creating release assets. Existing
release checksum generation and upload steps include the new JARs and POM.
