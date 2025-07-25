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


package com.tantivy4spark.core

import org.apache.spark.sql.sources._
import org.slf4j.LoggerFactory

object FiltersToQueryConverter {
  
  private val logger = LoggerFactory.getLogger(this.getClass)

  def convert(filters: Array[Filter]): String = {
    if (filters.isEmpty) {
      return ""
    }

    val queryParts = filters.flatMap(convertFilter).filter(_.nonEmpty)
    
    if (queryParts.isEmpty) {
      ""
    } else if (queryParts.length == 1) {
      queryParts.head
    } else {
      queryParts.mkString("(", ") AND (", ")")
    }
  }

  private def convertFilter(filter: Filter): Option[String] = {
    val query = filter match {
      case EqualTo(attribute, value) =>
        s"""$attribute:"${escapeValue(value)}""""
      
      case EqualNullSafe(attribute, value) =>
        if (value == null) {
          s"NOT _exists_:$attribute"
        } else {
          s"""$attribute:"${escapeValue(value)}""""
        }
      
      case GreaterThan(attribute, value) =>
        s"$attribute:{${escapeValue(value)} TO *}"
      
      case GreaterThanOrEqual(attribute, value) =>
        s"$attribute:[${escapeValue(value)} TO *]"
      
      case LessThan(attribute, value) =>
        s"$attribute:{* TO ${escapeValue(value)}}"
      
      case LessThanOrEqual(attribute, value) =>
        s"$attribute:[* TO ${escapeValue(value)}]"
      
      case In(attribute, values) =>
        val valueStrs = values.map(v => s""""${escapeValue(v)}"""").mkString(" OR ")
        s"$attribute:($valueStrs)"
      
      case IsNull(attribute) =>
        s"NOT _exists_:$attribute"
      
      case IsNotNull(attribute) =>
        s"_exists_:$attribute"
      
      case And(left, right) =>
        (convertFilter(left), convertFilter(right)) match {
          case (Some(l), Some(r)) => s"($l) AND ($r)"
          case (Some(l), None) => l
          case (None, Some(r)) => r
          case (None, None) => ""
        }
      
      case Or(left, right) =>
        (convertFilter(left), convertFilter(right)) match {
          case (Some(l), Some(r)) => s"($l) OR ($r)"
          case (Some(l), None) => l
          case (None, Some(r)) => r
          case (None, None) => ""
        }
      
      case Not(child) =>
        convertFilter(child) match {
          case Some(childQuery) => s"NOT ($childQuery)"
          case None => ""
        }
      
      case StringStartsWith(attribute, value) =>
        s"$attribute:${escapeValue(value)}*"
      
      case StringEndsWith(attribute, value) =>
        s"$attribute:*${escapeValue(value)}"
      
      case StringContains(attribute, value) =>
        s"$attribute:*${escapeValue(value)}*"
      
      case _ =>
        logger.warn(s"Unsupported filter: $filter")
        ""
    }

    if (query.nonEmpty) Some(query) else None
  }

  private def escapeValue(value: Any): String = {
    val str = value.toString
    // Escape special characters for Tantivy query syntax
    str.replaceAll("""([\+\-\!\(\)\{\}\[\]\^\~\*\?\:\\"])""", """\\$1""")
  }
}