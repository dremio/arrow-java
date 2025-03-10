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
package org.apache.arrow.flatbuf;

import com.google.flatbuffers.BaseVector;
import com.google.flatbuffers.BooleanVector;
import com.google.flatbuffers.ByteVector;
import com.google.flatbuffers.Constants;
import com.google.flatbuffers.DoubleVector;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.FloatVector;
import com.google.flatbuffers.IntVector;
import com.google.flatbuffers.LongVector;
import com.google.flatbuffers.ShortVector;
import com.google.flatbuffers.StringVector;
import com.google.flatbuffers.Struct;
import com.google.flatbuffers.Table;
import com.google.flatbuffers.UnionVector;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ----------------------------------------------------------------------
 * A field represents a named column in a record / row batch or child of a
 * nested type.
 */
@SuppressWarnings("unused")
public final class Field extends Table {
  public static void ValidateVersion() { Constants.FLATBUFFERS_25_2_10(); }
  public static Field getRootAsField(ByteBuffer _bb) { return getRootAsField(_bb, new Field()); }
  public static Field getRootAsField(ByteBuffer _bb, Field obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { __reset(_i, _bb); }
  public Field __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  /**
   * Name is not required, in i.e. a List
   */
  public String name() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer nameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public ByteBuffer nameInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 4, 1); }
  /**
   * Whether or not this field can contain nulls. Should be true in general.
   */
  public boolean nullable() { int o = __offset(6); return o != 0 ? 0!=bb.get(o + bb_pos) : false; }
  public byte typeType() { int o = __offset(8); return o != 0 ? bb.get(o + bb_pos) : 0; }
  /**
   * This is the type of the decoded value if the field is dictionary encoded.
   */
  public Table type(Table obj) { int o = __offset(10); return o != 0 ? __union(obj, o + bb_pos) : null; }
  /**
   * Present only if the field is dictionary encoded.
   */
  public org.apache.arrow.flatbuf.DictionaryEncoding dictionary() { return dictionary(new org.apache.arrow.flatbuf.DictionaryEncoding()); }
  public org.apache.arrow.flatbuf.DictionaryEncoding dictionary(org.apache.arrow.flatbuf.DictionaryEncoding obj) { int o = __offset(12); return o != 0 ? obj.__assign(__indirect(o + bb_pos), bb) : null; }
  /**
   * children apply only to nested data types like Struct, List and Union. For
   * primitive types children will have length 0.
   */
  public org.apache.arrow.flatbuf.Field children(int j) { return children(new org.apache.arrow.flatbuf.Field(), j); }
  public org.apache.arrow.flatbuf.Field children(org.apache.arrow.flatbuf.Field obj, int j) { int o = __offset(14); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int childrenLength() { int o = __offset(14); return o != 0 ? __vector_len(o) : 0; }
  public org.apache.arrow.flatbuf.Field.Vector childrenVector() { return childrenVector(new org.apache.arrow.flatbuf.Field.Vector()); }
  public org.apache.arrow.flatbuf.Field.Vector childrenVector(org.apache.arrow.flatbuf.Field.Vector obj) { int o = __offset(14); return o != 0 ? obj.__assign(__vector(o), 4, bb) : null; }
  /**
   * User-defined metadata
   */
  public org.apache.arrow.flatbuf.KeyValue customMetadata(int j) { return customMetadata(new org.apache.arrow.flatbuf.KeyValue(), j); }
  public org.apache.arrow.flatbuf.KeyValue customMetadata(org.apache.arrow.flatbuf.KeyValue obj, int j) { int o = __offset(16); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int customMetadataLength() { int o = __offset(16); return o != 0 ? __vector_len(o) : 0; }
  public org.apache.arrow.flatbuf.KeyValue.Vector customMetadataVector() { return customMetadataVector(new org.apache.arrow.flatbuf.KeyValue.Vector()); }
  public org.apache.arrow.flatbuf.KeyValue.Vector customMetadataVector(org.apache.arrow.flatbuf.KeyValue.Vector obj) { int o = __offset(16); return o != 0 ? obj.__assign(__vector(o), 4, bb) : null; }

  public static int createField(FlatBufferBuilder builder,
      int nameOffset,
      boolean nullable,
      byte typeType,
      int typeOffset,
      int dictionaryOffset,
      int childrenOffset,
      int customMetadataOffset) {
    builder.startTable(7);
    Field.addCustomMetadata(builder, customMetadataOffset);
    Field.addChildren(builder, childrenOffset);
    Field.addDictionary(builder, dictionaryOffset);
    Field.addType(builder, typeOffset);
    Field.addName(builder, nameOffset);
    Field.addTypeType(builder, typeType);
    Field.addNullable(builder, nullable);
    return Field.endField(builder);
  }

  public static void startField(FlatBufferBuilder builder) { builder.startTable(7); }
  public static void addName(FlatBufferBuilder builder, int nameOffset) { builder.addOffset(0, nameOffset, 0); }
  public static void addNullable(FlatBufferBuilder builder, boolean nullable) { builder.addBoolean(1, nullable, false); }
  public static void addTypeType(FlatBufferBuilder builder, byte typeType) { builder.addByte(2, typeType, 0); }
  public static void addType(FlatBufferBuilder builder, int typeOffset) { builder.addOffset(3, typeOffset, 0); }
  public static void addDictionary(FlatBufferBuilder builder, int dictionaryOffset) { builder.addOffset(4, dictionaryOffset, 0); }
  public static void addChildren(FlatBufferBuilder builder, int childrenOffset) { builder.addOffset(5, childrenOffset, 0); }
  public static int createChildrenVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startChildrenVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static void addCustomMetadata(FlatBufferBuilder builder, int customMetadataOffset) { builder.addOffset(6, customMetadataOffset, 0); }
  public static int createCustomMetadataVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startCustomMetadataVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endField(FlatBufferBuilder builder) {
    int o = builder.endTable();
    return o;
  }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) { __reset(_vector, _element_size, _bb); return this; }

    public Field get(int j) { return get(new Field(), j); }
    public Field get(Field obj, int j) {  return obj.__assign(__indirect(__element(j), bb), bb); }
  }
}
