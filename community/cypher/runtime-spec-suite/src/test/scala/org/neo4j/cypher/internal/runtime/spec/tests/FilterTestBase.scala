/*
 * Copyright (c) 2002-2019 "Neo4j,"
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
package org.neo4j.cypher.internal.runtime.spec.tests

import org.neo4j.cypher.internal.runtime.spec._
import org.neo4j.cypher.internal.{CypherRuntime, RuntimeContext}

abstract class FilterTestBase[CONTEXT <: RuntimeContext](
                                                          edition: Edition[CONTEXT],
                                                          runtime: CypherRuntime[CONTEXT],
                                                          sizeHint: Int
                                                        ) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  test("should filter by one predicate") {
    // given
    val input = inputValues((0 until sizeHint).map(Array[Any](_)):_*)
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("i")
      .filter(s"i >= ${sizeHint / 2}")
      .input(variables = Seq("i"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, input)

    // then
    val expected = (0 until sizeHint).filter(i => i >= sizeHint / 2)
    runtimeResult should beColumns("i").withRows(singleColumn(expected))
  }

  test("should filter by multiple predicate") {
    // given
    val input = inputValues((0 until sizeHint).map(Array[Any](_)):_*)
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("i")
      .filter(s"i >= ${sizeHint / 2}", "i % 2 = 0")
      .input(variables = Seq("i"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, input)

    // then
    val expected = (0 until sizeHint).filter(i => i >= sizeHint / 2 && i % 2 == 0)
    runtimeResult should beColumns("i").withRows(singleColumn(expected))
  }

  test("should work on empty input") {
    // given
    val input = inputValues()
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("i")
      .filter(s"i >= ${sizeHint / 2}", "i % 2 = 0")
      .input(variables = Seq("i"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, input)

    // then
    runtimeResult should beColumns("i").withNoRows()
  }
}
