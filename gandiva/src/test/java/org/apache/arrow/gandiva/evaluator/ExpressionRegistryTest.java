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
package org.apache.arrow.gandiva.evaluator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;
import java.util.Set;
import org.apache.arrow.gandiva.exceptions.GandivaException;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.junit.jupiter.api.Test;

public class ExpressionRegistryTest {

  @Test
  public void testTypes() throws GandivaException {
    Set<ArrowType> types = ExpressionRegistry.getInstance().getSupportedTypes();
    ArrowType.Int uint8 = new ArrowType.Int(8, false);
    assertTrue(types.contains(uint8));
  }

  @Test
  public void testTimeTypesKeepBitWidth() throws GandivaException {
    Set<ArrowType> types = ExpressionRegistry.getInstance().getSupportedTypes();
    assertTrue(types.contains(new ArrowType.Time(TimeUnit.SECOND, 32)));
    assertTrue(types.contains(new ArrowType.Time(TimeUnit.MILLISECOND, 32)));
    assertTrue(types.contains(new ArrowType.Time(TimeUnit.MICROSECOND, 64)));
    assertTrue(types.contains(new ArrowType.Time(TimeUnit.NANOSECOND, 64)));
    // Time64 units used to be reported with a 32-bit width.
    assertFalse(types.contains(new ArrowType.Time(TimeUnit.MICROSECOND, 32)));
    assertFalse(types.contains(new ArrowType.Time(TimeUnit.NANOSECOND, 32)));
  }

  @Test
  public void testTime64Functions() throws GandivaException {
    // Time64 signatures are registered with MICROSECOND; NANOSECOND arguments match the
    // same signature in Gandiva and are remapped at codegen time.
    ArrowType.Time timeMicro = new ArrowType.Time(TimeUnit.MICROSECOND, 64);
    ArrowType.Int int64 = new ArrowType.Int(64, true);
    ArrowType.Bool bool = new ArrowType.Bool();
    Set<FunctionSignature> functions = ExpressionRegistry.getInstance().getSupportedFunctions();
    assertTrue(
        functions.contains(
            new FunctionSignature("extractHour", int64, Lists.newArrayList(timeMicro))));
    assertTrue(
        functions.contains(
            new FunctionSignature("less_than", bool, Lists.newArrayList(timeMicro, timeMicro))));
    assertTrue(
        functions.contains(new FunctionSignature("isnull", bool, Lists.newArrayList(timeMicro))));
  }

  @Test
  public void testFunctions() throws GandivaException {
    ArrowType.Int uint8 = new ArrowType.Int(8, false);
    FunctionSignature signature =
        new FunctionSignature("add", uint8, Lists.newArrayList(uint8, uint8));
    Set<FunctionSignature> functions = ExpressionRegistry.getInstance().getSupportedFunctions();
    assertTrue(functions.contains(signature));
  }

  @Test
  public void testFunctionAliases() throws GandivaException {
    ArrowType.Int int64 = new ArrowType.Int(64, true);
    FunctionSignature signature =
        new FunctionSignature("modulo", int64, Lists.newArrayList(int64, int64));
    Set<FunctionSignature> functions = ExpressionRegistry.getInstance().getSupportedFunctions();
    assertTrue(functions.contains(signature));
  }

  @Test
  public void testCaseInsensitiveFunctionName() throws GandivaException {
    ArrowType.Utf8 utf8 = new ArrowType.Utf8();
    ArrowType.Int int64 = new ArrowType.Int(64, true);
    FunctionSignature signature =
        new FunctionSignature("castvarchar", utf8, Lists.newArrayList(utf8, int64));
    Set<FunctionSignature> functions = ExpressionRegistry.getInstance().getSupportedFunctions();
    assertTrue(functions.contains(signature));
  }
}
