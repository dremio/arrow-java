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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.arrow.gandiva.expression.TreeBuilder;
import org.apache.arrow.gandiva.ipc.GandivaTypes.SelectionVectorType;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.jupiter.api.Test;

class CacheOnlyEvaluatorTest extends BaseEvaluatorTest {
  @Test
  void projectorMissDoesNotCompileAndOptimizationKeysAreDistinct() throws Exception {
    Field input = Field.nullable("java_cache_only_project", new ArrowType.Int(32, true));
    Schema schema = new Schema(List.of(input));
    var expr =
        TreeBuilder.makeExpression(
            TreeBuilder.makeFunction(
                "add",
                List.of(TreeBuilder.makeField(input), TreeBuilder.makeField(input)),
                input.getType()),
            Field.nullable("result", input.getType()));
    var o3 = new ConfigurationBuilder.ConfigOptions().withOptimize(true);
    var o0 = new ConfigurationBuilder.ConfigOptions().withOptimize(false);
    assertTrue(Projector.tryMakeFromCache(schema, List.of(expr), o3).isEmpty());
    assertTrue(Projector.tryMakeFromCache(schema, List.of(expr), o3).isEmpty());
    var o0Evaluator = Projector.make(schema, List.of(expr), o0);
    try {
      assertTrue(Projector.tryMakeFromCache(schema, List.of(expr), o3).isEmpty());
    } finally {
      o0Evaluator.close();
    }
    var o3Evaluator = Projector.make(schema, List.of(expr), o3);
    try {
      var hit = Projector.tryMakeFromCache(schema, List.of(expr), o3);
      assertTrue(hit.isPresent());
      hit.orElseThrow().close();
      assertTrue(
          Projector.tryMakeFromCache(schema, List.of(expr), SelectionVectorType.SV_INT16, o3)
              .isEmpty());
      assertTrue(
          Projector.tryMakeFromCache(
                  schema,
                  List.of(expr),
                  new ConfigurationBuilder.ConfigOptions().withOptimize(true).withTargetCPU(false))
              .isEmpty());
    } finally {
      o3Evaluator.close();
    }
  }

  @Test
  void filterMissDoesNotCompileAndOptimizationKeysAreDistinct() throws Exception {
    Field input = Field.nullable("java_cache_only_filter", new ArrowType.Int(32, true));
    Schema schema = new Schema(List.of(input));
    var condition =
        TreeBuilder.makeCondition(
            TreeBuilder.makeFunction(
                "less_than",
                List.of(TreeBuilder.makeField(input), TreeBuilder.makeLiteral(7)),
                new ArrowType.Bool()));
    var o3 = new ConfigurationBuilder.ConfigOptions().withOptimize(true);
    var o0 = new ConfigurationBuilder.ConfigOptions().withOptimize(false);
    assertTrue(Filter.tryMakeFromCache(schema, condition, o3).isEmpty());
    assertTrue(Filter.tryMakeFromCache(schema, condition, o3).isEmpty());
    var o0Evaluator = Filter.make(schema, condition, o0);
    try {
      assertTrue(Filter.tryMakeFromCache(schema, condition, o3).isEmpty());
    } finally {
      o0Evaluator.close();
    }
    var o3Evaluator = Filter.make(schema, condition, o3);
    try {
      var hit = Filter.tryMakeFromCache(schema, condition, o3);
      assertTrue(hit.isPresent());
      hit.orElseThrow().close();
    } finally {
      o3Evaluator.close();
    }
  }
}
