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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 保存在某个SqlSession的某个事务中需要向某个二级缓存中添加的缓存数据
 *
 * The 2nd level cache transactional buffer.
 * 
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back. 
 * Blocking cache support has been added. Therefore any get() that returns a cache miss 
 * will be followed by a put() so any lock associated with the key can be released. 
 * 
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  // 二级缓存对应的Cache对象
  private Cache delegate;
  // 值为true时，则表示当前 TransactionalCache 不可查询，且提交事务时会将底层 Cache 清空
  private boolean clearOnCommit;
  // 暂时记录添加到 TransactionalCache 中的数据。在事务提交时，会将其中的数据添加到二级缓存中
  private Map<Object, Object> entriesToAddOnCommit;
  // 记录缓存未命中的 CacheKey 对象
  private Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<Object, Object>();
    this.entriesMissedInCache = new HashSet<Object>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // issue #116
    // 从缓存中查找，如果为空则保存到未民众的cache中
    Object object = delegate.getObject(key);
    if (object == null) {
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) { // 为true，返回null
      return null;
    } else {
      return object; // 从底层cache中查询到的值
    }
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  // 暂存在 entriesToAddOnCommit 中
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear(); // 清空暂存区
  }

  public void commit() {
    if (clearOnCommit) { // 清空二级缓存
      delegate.clear();
    }
    flushPendingEntries(); // 将暂存的 entriesToAddOnCommit 数据保存到二级缓存中
    // 重置 clearOnCommit、entriesToAddOnCommit、entriesMissedInCache
    reset();
  }

  public void rollback() {
    // 从二级缓存中删除 entriesMissedInCache 中的数据
    unlockMissedEntries();
    // 重置 clearOnCommit、entriesToAddOnCommit、entriesMissedInCache
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    // 将entriesToAddOnCommit保存到Cache中
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    // 遍历entriesMissedInCache集合，将不包含在entriesToAddOnCommit中的添加到二级Cache中
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter."
            + "Consider upgrading your cache adapter to the latest version.  Cause: " + e);
      }
    }
  }

}
