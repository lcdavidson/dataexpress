/*
Copyright (c) 2012, The Children's Hospital of Philadelphia All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
   disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
   following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.chop.cbmi.dataExpress.backends

import edu.chop.cbmi.dataExpress.dataModels.sql._
import edu.chop.cbmi.dataExpress.dataModels.DataType

/**
 * Created by IntelliJ IDEA.
 * User: italiam
 * Date: 8/18/11
 * Time: 3:22 PM
 * To change this template use File | Settings | File Templates.
 */

case object MySqlDialect extends SqlDialect {
  val identifierQuote = "`"
  val tableCreate = "CREATE TABLE"
  val tableTruncate = "TRUNCATE TABLE"
  val tableDrop = "DROP TABLE"
  val tableCascade = "CASCADE"
  val insertStatement = "INSERT INTO"
  val insertValues = "VALUES"
  val transactionStart = "START TRANSACTION"
  val transactionEnd = "COMMIT"
  val transactionCommit = "COMMIT"
  val transactionRollback = "ROLLBACK"

  def quoteIdentifier(id: String) = {
    "%s%s%s".format(identifierQuote, id, identifierQuote)
  }

  def createTable(name: String, columns: List[(String, DataType)], schemaName: String) = {
    var quotedSchemaPrefix: String = null
    if (schemaName == null) quotedSchemaPrefix = "" else quotedSchemaPrefix = quoteIdentifier(schemaName) + "."

    val createBlock = columns.map {
      (t: Tuple2[String, DataType]) => "%s %s".format(quoteIdentifier(t._1), toSqlString(t._2))
    }
    val createString = "%s %s%s (%s)".format(tableCreate, quotedSchemaPrefix, quoteIdentifier(name), createBlock.mkString(", "))
    createString
  }

  def dropTable(name: String, cascade: Boolean = false, schemaName: String = null) = {
    var quotedSchemaPrefix: String = null
    if (schemaName == null) quotedSchemaPrefix = "" else quotedSchemaPrefix = quoteIdentifier(schemaName) + "."

    val fmtString = if (cascade) "%s %s%s " + tableCascade else "%s %s%s"
    fmtString.format(tableDrop, quotedSchemaPrefix, quoteIdentifier(name))
  }

  def truncate(table: String, schemaName: String = null) = {
    var quotedSchemaPrefix: String = null
    if (schemaName == null) quotedSchemaPrefix = "" else quotedSchemaPrefix = quoteIdentifier(schemaName) + "."

    "%s %s%s".format(tableTruncate, quotedSchemaPrefix, quoteIdentifier(table))
  }

  def commit() = transactionCommit

  def rollback() = transactionRollback

  def endTransaction() = transactionEnd

  def startTransaction() = transactionStart

  def insertRecord(tableName: String, columnNames: List[String], schemaName: String = null) = {
    val valuesList = "%s".format(List.fill(columnNames.size)("?").mkString(","))

    var quotedSchemaPrefix: String = null
    if (schemaName == null) quotedSchemaPrefix = "" else quotedSchemaPrefix = quoteIdentifier(schemaName) + "."

    val sqlString = "%s %s%s (%s) %s (%s)".format(insertStatement,
      quotedSchemaPrefix,
      quoteIdentifier(tableName),
      columnNames.map {
        n => quoteIdentifier(n)
      }.mkString(","),
      insertValues,
      valuesList)
    sqlString
  }

  //TODO: Create a "WHERE" clause object so we can do more than equality here
  def updateRecords(tableName: String, columnNames: List[String], filter: List[(String, Any)], schemaName: String = null) = {
    var quotedSchemaPrefix: String = null
    if (schemaName == null) quotedSchemaPrefix = "" else quotedSchemaPrefix = quoteIdentifier(schemaName) + "."

    val setString = columnNames.map {
      i => "%s = ?".format(quoteIdentifier(i))
    }.mkString(", ")
    val whereString = if (filter.size > 0) {
      "WHERE %s".format(filter.map {
        i => quoteIdentifier(i._1) + " = ?"
      }.mkString(" AND "))
    }
    else {
      "" //Empty string so we don't cause errors when doing bulk updates
    }
    val sqlString = "UPDATE %s%s SET %s %s".format(quotedSchemaPrefix, quoteIdentifier(tableName), setString, whereString)
    sqlString
  }


  def toSqlString(dataType: DataType): String = {
    dataType match {
      case IntegerDataType()                                => "INTEGER"
      case SmallIntegerDataType()                           => "SMALLINT"
      //TODO: Remove blatant Postgresql hack for lack of tinyint support below
      case TinyIntegerDataType()                            => "SMALLINT"
      case FloatDataType(p)                                 => {
        if (p <= 53)    "FLOAT(%d)".format(p)
        else            "FLOAT(53)"
      }
      case DecimalDataType(p, s)                            => "DECIMAL(%d,%d)".format(p, s)
      case DateDataType()                                   => "DATE"
      //case DateTimeDataType(true)                           => "TIMESTAMP WITH TIME ZONE"
      //MySql does not have TIME STAMP WITH TIMEZONE
      //Must run off   jdbcCompliantTruncation for these to work by setting jdbcCompliantTruncation=false
      //For Connection
      //http://bugs.mysql.com/bug.php?id=21544
      //case DateTimeDataType(true)                           => "DATETIME"
      case DateTimeDataType(true)                           => "TIMESTAMP"
      case DateTimeDataType(false)                          => "TIMESTAMP"
      //Was Causing : MysqlDataTruncation: Data truncation: Incorrect datetime value, with certain dates
      //MySQL does not accept TIMESTAMP values that include a zero in the day or month column
      //or values that are not a valid date. The sole exception to this rule is the special “zero” value '0000-00-00 00:00:00'.
      //http://dev.mysql.com/doc/refman/5.1/en/news-5-1-4.html
      //http://dev.mysql.com/doc/refman/5.1/en/datetime.html
      //case DateTimeDataType(false)                          => "DATETIME"
      //MySQL does not have TIME WITH TIME ZONE
      case TimeDataType(true)                               => "TIME WITH TIME ZONE"
      case TimeDataType(false)                              => "TIME"
      case CharacterDataType(length, fixed)                 => {
        if (fixed) {
          if    (length <= 255)   "CHAR(%d)".format(length)
          else                    "TEXT"
        }
        else "VARCHAR(%d)".format(length)
      }
      case TextDataType()                                   => "TEXT"
      case BigBinaryDataType()                              => "BLOB"
      case BooleanDataType()                                => "BOOLEAN"
      case BitDataType()                                    => "BIT"
      case _                                                => ""
    }
  }

}