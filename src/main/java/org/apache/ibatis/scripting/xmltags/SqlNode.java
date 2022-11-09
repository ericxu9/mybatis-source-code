/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

/**
 * @author Clinton Begin
 */
public interface SqlNode {
  /**
   * 解析 SqlNode 所记录的动态 SQL 节点，并调用 DynamicContext.appendSql() 方法将解析后的sql追加起来，当sql节点下所有 SqlNode完成解析后
   * 通过 DynamicContext.getSql() 就可以获取到完整的sql
   */
  boolean apply(DynamicContext context);
}
