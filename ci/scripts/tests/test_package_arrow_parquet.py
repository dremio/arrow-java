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

import importlib.util
import json
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "package_arrow_parquet.py"
SPEC = importlib.util.spec_from_file_location("package_arrow_parquet", SCRIPT)
package = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(package)


class TestParquetRelease(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.source.mkdir()
        (self.source / "pom.xml").write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0">'
            "<version>19.0.0</version></project>"
        )
        for name in ("LICENSE.txt", "NOTICE.txt"):
            (self.source / name).write_text(name)
        self.staged = self.root / "staged"
        for classifier in package.REQUIRED_PLATFORMS:
            directory = self.staged / classifier
            entries = [f"lib/{name}" for name in package.LIBRARIES]
            entries += ["include/arrow/api.h", "lib/cmake/Parquet/ParquetConfig.cmake"]
            for name in entries:
                path = directory / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(name)
            self.manifest(directory)
        self.output = self.root / "output"

    def manifest(self, directory, revision="a" * 40):
        manifest = {
            "schema_version": 1,
            "classifier": directory.name,
            "arrow_cpp_sha": revision,
            "arrow_cpp_version": "23.0.1",
            "cxx_standard": 20,
            "codecs": ["snappy", "zlib", "zstd"],
            "sha256": {
                str(p.relative_to(directory)): package.sha256(p)
                for p in package.files(directory)
                if p.name != "manifest.json"
            },
        }
        (directory / "manifest.json").write_text(json.dumps(manifest))

    def test_native_artifacts_have_matching_coordinates_and_contents(self):
        package.publish(self.source, self.staged, self.output)
        expected = {"arrow-parquet-19.0.0.jar", "arrow-parquet-19.0.0.pom"}
        expected |= {
            f"arrow-parquet-19.0.0-{p}.jar" for p in package.REQUIRED_PLATFORMS
        }
        self.assertEqual(expected, {p.name for p in self.output.iterdir()})
        root = ET.parse(self.output / "arrow-parquet-19.0.0.pom").getroot()
        ns = "{http://maven.apache.org/POM/4.0.0}"
        self.assertEqual(
            ["org.apache.arrow", "arrow-parquet", "19.0.0", "jar"],
            [
                root.find(ns + k).text
                for k in ("groupId", "artifactId", "version", "packaging")
            ],
        )
        for classifier in package.REQUIRED_PLATFORMS:
            directory = self.staged / classifier
            with zipfile.ZipFile(
                self.output / f"arrow-parquet-19.0.0-{classifier}.jar"
            ) as jar:
                self.assertEqual(
                    {
                        str(p.relative_to(directory)): p.read_bytes()
                        for p in package.files(directory)
                    },
                    {name: jar.read(name) for name in jar.namelist()},
                )

    def test_missing_platform_fails_before_publication(self):
        shutil.rmtree(self.staged / "linux-x86_64")
        with self.assertRaisesRegex(ValueError, "Missing release platforms"):
            package.publish(self.source, self.staged, self.output)
        self.assertFalse(self.output.exists())

    def test_mixed_arrow_revisions_fail_before_publication(self):
        self.manifest(self.staged / "linux-x86_64", "b" * 40)
        with self.assertRaisesRegex(ValueError, "different Arrow revisions"):
            package.publish(self.source, self.staged, self.output)
        self.assertFalse(self.output.exists())

    def test_changed_library_is_rejected(self):
        (self.staged / "linux-x86_64/lib/libparquet.a").write_text("changed")
        with self.assertRaisesRegex(ValueError, "do not match manifest"):
            package.publish(self.source, self.staged, self.output)

    def test_unexpected_file_is_rejected(self):
        (self.staged / "linux-x86_64/extra.so").write_text("unexpected")
        with self.assertRaisesRegex(ValueError, "do not match manifest"):
            package.publish(self.source, self.staged, self.output)

    def test_wrong_platform_manifest_is_rejected(self):
        directory = self.staged / "linux-x86_64"
        manifest = json.loads((directory / "manifest.json").read_text())
        manifest["classifier"] = "osx-aarch_64"
        (directory / "manifest.json").write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "Invalid platform manifest"):
            package.publish(self.source, self.staged, self.output)


if __name__ == "__main__":
    unittest.main()
