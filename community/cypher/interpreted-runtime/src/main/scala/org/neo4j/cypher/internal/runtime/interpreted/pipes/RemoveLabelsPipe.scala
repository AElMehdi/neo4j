/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal.runtime.interpreted.ExecutionContext
import org.neo4j.cypher.internal.runtime.interpreted.CastSupport
import org.neo4j.cypher.internal.runtime.interpreted.GraphElementPropertyFunctions
import org.neo4j.cypher.internal.v4_0.util.attribution.Id
import org.neo4j.values.storable.Values
import org.neo4j.values.virtual.VirtualNodeValue

case class RemoveLabelsPipe(src: Pipe, variable: String, labels: Seq[LazyLabel])
                           (val id: Id = Id.INVALID_ID)
  extends PipeWithSource(src) with GraphElementPropertyFunctions {

  override protected def internalCreateResults(input: Iterator[ExecutionContext],
                                               state: QueryState): Iterator[ExecutionContext] = {
    input.map { row =>
      val item = row.get(variable).get
      if (item != Values.NO_VALUE) removeLabels(row, state, CastSupport.castOrFail[VirtualNodeValue](item).id)
      row
    }
  }

  private def removeLabels(context: ExecutionContext, state: QueryState, nodeId: Long) = {
    val labelIds = labels.flatMap(_.getOptId(state.query)).map(_.id)
    state.query.removeLabelsFromNode(nodeId, labelIds.iterator)
  }
}
