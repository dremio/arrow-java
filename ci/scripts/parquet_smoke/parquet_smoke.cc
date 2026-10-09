// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

#include <iostream>

#include "arrow/api.h"
#include "arrow/compute/initialize.h"
#include "arrow/io/api.h"
#include "parquet/arrow/reader.h"
#include "parquet/arrow/writer.h"

arrow::Status CheckPackage() {
  ARROW_RETURN_NOT_OK(arrow::compute::Initialize());
  arrow::Int32Builder builder;
  ARROW_RETURN_NOT_OK(builder.Append(1));
  ARROW_RETURN_NOT_OK(builder.AppendNull());
  ARROW_RETURN_NOT_OK(builder.Append(3));
  ARROW_ASSIGN_OR_RAISE(auto values, builder.Finish());
  const auto table = arrow::Table::Make(
      arrow::schema({arrow::field("values", arrow::int32())}), {values});
  for (const auto codec : {parquet::Compression::SNAPPY, parquet::Compression::GZIP,
                           parquet::Compression::ZSTD}) {
    ARROW_ASSIGN_OR_RAISE(auto output, arrow::io::BufferOutputStream::Create());
    auto properties = parquet::WriterProperties::Builder().compression(codec)->build();
    ARROW_RETURN_NOT_OK(parquet::arrow::WriteTable(*table, arrow::default_memory_pool(),
                                                   output, 2, properties));
    ARROW_ASSIGN_OR_RAISE(auto buffer, output->Finish());
    ARROW_ASSIGN_OR_RAISE(
        auto reader,
        parquet::arrow::OpenFile(std::make_shared<arrow::io::BufferReader>(buffer),
                                 arrow::default_memory_pool()));
    std::shared_ptr<arrow::Table> actual;
    ARROW_RETURN_NOT_OK(reader->ReadTable(&actual));
    if (!table->Equals(*actual)) {
      return arrow::Status::Invalid("Parquet codec round trip changed the table");
    }
  }
  return arrow::Status::OK();
}

int main() {
  const auto status = CheckPackage();
  if (!status.ok()) {
    std::cerr << status << std::endl;
    return 1;
  }
  return 0;
}
