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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator
 * 清除最近最少使用的数据
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  private final Cache delegate;
  private Map<Object, Object> keyMap;
  private Object eldestKey; // 最少使用的key

  public LruCache(Cache delegate) {
    this.delegate = delegate;
    setSize(1024); // 默认1024
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) {
    // 重置keyMap字段
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) { // accessOrder=true，当get是会改变其记录的顺序（将其移动到链表尾部）
      private static final long serialVersionUID = 4267176411845948333L;

      // 重写 LinkedHashMap 的 removeEldestEntry 方法，当调用 put方法时会调用该方法
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) { // 如果达到上限，更新eldestKey，后面会删除
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    cycleKeyList(key); // 删除最久未使用的缓存
  }

  @Override
  public Object getObject(Object key) {
    // 移动到队列尾部
    keyMap.get(key); //touch
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void cycleKeyList(Object key) {
    keyMap.put(key, key); // 上面keyMap初始化的时候重写了 removeEldestEntry 方法，那里面会判断是否达到上限
    if (eldestKey != null) { // 不为空，说明到了缓存上限
      delegate.removeObject(eldestKey); // 删除未使用的缓存key
      eldestKey = null;
    }
  }

}
