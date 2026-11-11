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
package org.apache.arrow.gandiva.expression;

import java.util.List;
import java.util.Objects;
import org.apache.arrow.gandiva.exceptions.GandivaException;
import org.apache.arrow.gandiva.ipc.GandivaTypes;
import org.apache.arrow.vector.types.pojo.ArrowType;

/** An ordered CASE expression without a nested ELSE chain. */
class CaseNode implements TreeNode {
  private final List<TreeNode> conditions;
  private final List<TreeNode> results;
  private final TreeNode elseNode;
  private final ArrowType retType;

  CaseNode(
      List<TreeNode> conditions, List<TreeNode> results, TreeNode elseNode, ArrowType retType) {
    if (conditions.isEmpty() || conditions.size() != results.size()) {
      throw new IllegalArgumentException("CASE requires matching conditions and results");
    }
    this.conditions = List.copyOf(conditions);
    this.results = List.copyOf(results);
    this.elseNode = Objects.requireNonNull(elseNode);
    this.retType = Objects.requireNonNull(retType);
  }

  @Override
  public GandivaTypes.TreeNode toProtobuf() throws GandivaException {
    GandivaTypes.CaseNode.Builder caseNode = GandivaTypes.CaseNode.newBuilder();
    for (int i = 0; i < conditions.size(); i++) {
      caseNode.addConditions(conditions.get(i).toProtobuf());
      caseNode.addResults(results.get(i).toProtobuf());
    }
    caseNode.setElseNode(elseNode.toProtobuf());
    caseNode.setReturnType(ArrowTypeHelper.arrowTypeToProtobuf(retType));
    return GandivaTypes.TreeNode.newBuilder().setCaseNode(caseNode).build();
  }
}
