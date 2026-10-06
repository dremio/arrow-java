/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.arrow.compression;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.compression.CompressionUtil;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Regression coverage for bounded frame buffers and multi-block interoperability. */
class TestLz4FrameBlockSize {
  @Test
  void preservesCompressionAcrossSmallBlockBoundaries() {
    byte[] pattern = new byte[50000];
    new Random(1323).nextBytes(pattern);
    byte[] expected = new byte[1000000];
    for (int i = 0; i < expected.length; i++) {
      expected[i] = pattern[i % pattern.length];
    }
    try (BufferAllocator allocator = new RootAllocator(Integer.MAX_VALUE)) {
      ArrowBuf input = allocator.buffer(expected.length);
      input.setBytes(0, expected);
      input.writerIndex(expected.length);
      Lz4CompressionCodec codec = new Lz4CompressionCodec();
      ArrowBuf compressed = codec.compress(allocator, input);
      long size = compressed.writerIndex();
      try (ArrowBuf restored = codec.decompress(allocator, compressed)) {
        byte[] actual = new byte[expected.length];
        restored.getBytes(0, actual);
        assertArrayEquals(expected, actual);
        assertTrue(size < expected.length / 4, "Large buffers must retain cross-block matches");
      }
    }
  }

  @Test
  void stillReadsFourMiBFrames() throws IOException {
    byte[] expected = new byte[131072];
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (FramedLZ4CompressorOutputStream out = new FramedLZ4CompressorOutputStream(bytes)) {
      out.write(expected);
    }
    try (BufferAllocator allocator = new RootAllocator(Integer.MAX_VALUE)) {
      ArrowBuf framed =
          allocator.buffer(CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH + bytes.size());
      framed.setBytes(
          0,
          ByteBuffer.allocate((int) CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH)
              .order(ByteOrder.LITTLE_ENDIAN)
              .putLong(expected.length)
              .array());
      framed.setBytes(CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH, bytes.toByteArray());
      framed.writerIndex(CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH + bytes.size());
      assertEquals(7, (framed.getByte(CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH + 5) >>> 4) & 7);
      try (ArrowBuf restored = new Lz4CompressionCodec().decompress(allocator, framed)) {
        byte[] actual = new byte[expected.length];
        restored.getBytes(0, actual);
        assertArrayEquals(expected, actual);
      }
    }
  }

  @ParameterizedTest
  @CsvSource({
    "1024, 4",
    "65536, 4",
    "65537, 5",
    "262144, 5",
    "262145, 6",
    "1048576, 6",
    "1048577, 7",
    "5242880, 7"
  })
  void usesAppropriateFrameBlocksAndRoundTrips(int length, int expectedBlockSizeId) {
    byte[] expected = new byte[length];
    for (int i = 0; i < length; i++) {
      expected[i] = (byte) (i % 17);
    }
    Lz4CompressionCodec codec = new Lz4CompressionCodec();
    try (BufferAllocator allocator = new RootAllocator(Integer.MAX_VALUE)) {
      ArrowBuf input = allocator.buffer(length);
      input.setBytes(0, expected);
      input.writerIndex(length);
      // compress and decompress each consume their input buffer.
      ArrowBuf compressed = codec.compress(allocator, input);
      try {
        // The LZ4 frame BD byte follows its four-byte magic and FLG byte.
        int blockSizeId =
            (compressed.getByte(CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH + 5) >>> 4) & 7;
        assertEquals(
            expectedBlockSizeId, blockSizeId, "LZ4 frame block allocation must fit the input");
      } catch (Throwable failure) {
        compressed.close();
        throw failure;
      }
      try (ArrowBuf restored = codec.decompress(allocator, compressed)) {
        assertEquals(length, restored.writerIndex());
        byte[] actual = new byte[length];
        restored.getBytes(0, actual);
        assertArrayEquals(expected, actual);
      }
    }
  }
}
