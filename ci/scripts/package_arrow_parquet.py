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

"""Stage and publish relocatable Arrow Parquet native development packages."""

import argparse
import hashlib
import json
import platform
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

PLATFORMS = {"linux-x86_64", "linux-aarch_64", "osx-x86_64", "osx-aarch_64"}
REQUIRED_PLATFORMS = {"linux-x86_64", "linux-aarch_64", "osx-aarch_64"}
LIBRARIES = (
    "libarrow.a",
    "libarrow_compute.a",
    "libparquet.a",
    "libarrow_bundled_dependencies.a",
)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def files(directory):
    return sorted(p for p in directory.rglob("*") if p.is_file())


def stage(arrow_source, install, output):
    git = [
        "git",
        "-c",
        f"safe.directory={arrow_source.resolve()}",
        "-C",
        str(arrow_source),
    ]
    git_root = subprocess.check_output(
        git + ["rev-parse", "--show-toplevel"], text=True
    ).strip()
    if Path(git_root).resolve() != arrow_source.resolve():
        raise ValueError("Arrow C++ source must be its own Git checkout")
    system = {"Linux": "linux", "Darwin": "osx"}[platform.system()]
    arch = {"x86_64": "x86_64", "arm64": "aarch_64", "aarch64": "aarch_64"}[
        platform.machine()
    ]
    classifier = f"{system}-{arch}"
    destination = output / classifier
    if destination.exists():
        raise ValueError(f"Refusing to overwrite {destination}")
    for name in LIBRARIES:
        if not (install / "lib" / name).is_file():
            raise ValueError(f"Missing static library {name}")
    for directory in ("include", "lib/cmake"):
        shutil.copytree(install / directory, destination / directory)
    for path in (install / "lib").glob("*.a"):
        shutil.copy2(path, destination / "lib" / path.name)
    legal = destination / "share" / "licenses" / "arrow"
    legal.mkdir(parents=True)
    for name in ("LICENSE.txt", "NOTICE.txt"):
        shutil.copy2(arrow_source / name, legal / name)
    cpp_sha = subprocess.check_output(git + ["rev-parse", "HEAD"], text=True).strip()
    config = (install / "include/arrow/util/config.h").read_text()
    version = re.search(r'#define ARROW_VERSION_STRING "([^"]+)"', config).group(1)
    manifest = {
        "schema_version": 1,
        "classifier": classifier,
        "arrow_cpp_sha": cpp_sha,
        "arrow_cpp_version": version,
        "cxx_standard": 20,
        "codecs": ["snappy", "zlib", "zstd"],
        "sha256": {
            str(path.relative_to(destination)): sha256(path)
            for path in files(destination)
        },
    }
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(destination)
    return destination


def validate(directory):
    manifest = json.loads((directory / "manifest.json").read_text())
    if manifest["schema_version"] != 1 or manifest["classifier"] != directory.name:
        raise ValueError(f"Invalid platform manifest in {directory}")
    if directory.name not in PLATFORMS:
        raise ValueError(f"Unsupported platform {directory.name}")
    if not re.fullmatch(r"[0-9a-f]{40}", manifest["arrow_cpp_sha"]):
        raise ValueError(f"Missing complete Arrow C++ revision in {directory}")
    if manifest["cxx_standard"] != 20 or manifest["codecs"] != [
        "snappy",
        "zlib",
        "zstd",
    ]:
        raise ValueError(f"Missing required codecs in {directory}")
    actual = {
        str(path.relative_to(directory)): sha256(path)
        for path in files(directory)
        if path != directory / "manifest.json"
    }
    if actual != manifest["sha256"]:
        raise ValueError(f"Package contents do not match manifest in {directory}")
    for name in LIBRARIES:
        if f"lib/{name}" not in actual:
            raise ValueError(f"Missing static library {name}")
    if not (directory / "lib/cmake/Parquet/ParquetConfig.cmake").is_file():
        raise ValueError(f"Missing Parquet CMake package in {directory}")
    return manifest


def archive(path, entries):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as output:
        for name, content in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            output.writestr(info, content)


def publish(source, staged, destination):
    directories = sorted(path for path in staged.iterdir() if path.is_dir())
    manifests = [validate(path) for path in directories]
    platforms = {manifest["classifier"] for manifest in manifests}
    if not REQUIRED_PLATFORMS.issubset(platforms):
        raise ValueError(f"Missing release platforms: {REQUIRED_PLATFORMS - platforms}")
    revisions = {(m["arrow_cpp_sha"], m["arrow_cpp_version"]) for m in manifests}
    if len(revisions) != 1:
        raise ValueError("Platform packages were built from different Arrow revisions")
    pom = ET.parse(source / "pom.xml").getroot()
    version = pom.find("{http://maven.apache.org/POM/4.0.0}version").text
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", version):
        raise ValueError("Invalid Maven version")
    destination.mkdir(parents=True, exist_ok=True)
    base = f"arrow-parquet-{version}"
    for directory in directories:
        entries = {
            str(p.relative_to(directory)): p.read_bytes() for p in files(directory)
        }
        archive(destination / f"{base}-{directory.name}.jar", entries)
    metadata = {
        "META-INF/LICENSE": (source / "LICENSE.txt").read_bytes(),
        "META-INF/NOTICE": (source / "NOTICE.txt").read_bytes(),
        "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\n\r\n",
        "META-INF/arrow-parquet.json": (
            json.dumps(manifests, indent=2) + "\n"
        ).encode(),
    }
    archive(destination / f"{base}.jar", metadata)
    (destination / f"{base}.pom").write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
        "  <modelVersion>4.0.0</modelVersion>\n"
        "  <groupId>org.apache.arrow</groupId>\n"
        "  <artifactId>arrow-parquet</artifactId>\n"
        f"  <version>{version}</version>\n"
        "  <packaging>jar</packaging>\n"
        "  <name>Arrow Parquet</name>\n"
        "  <description>Platform-classified static Arrow C++ Parquet libraries and headers</description>\n"
        "  <url>https://arrow.apache.org/</url>\n"
        "  <licenses><license><name>Apache License, Version 2.0</name>\n"
        "    <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>\n"
        "  </license></licenses>\n"
        "</project>\n"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    stage_parser = commands.add_parser("stage")
    for argument in ("arrow_source", "install", "output"):
        stage_parser.add_argument(argument, type=Path)
    publish_parser = commands.add_parser("publish")
    for argument in ("source", "staged", "destination"):
        publish_parser.add_argument(argument, type=Path)
    arguments = vars(parser.parse_args())
    command = arguments.pop("command")
    {"stage": stage, "publish": publish}[command](**arguments)
