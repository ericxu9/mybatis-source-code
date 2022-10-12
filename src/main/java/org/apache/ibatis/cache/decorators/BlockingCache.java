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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * Simple blocking decorator 
 * 
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 * 
 * @author Eduardo Macarron
 *
 */
// 保证一会有一个线程到数据库中查询key对应的数据
public class BlockingCache implements Cache {

  // 阻塞超时时长
  private long timeout;
  // 被装饰的底层 Cache 对象
  private final Cache delegate;
  private final ConcurrentHashMap<Object, ReentrantLock> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<Object, ReentrantLock>();
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
  public void putObject(Object key, Object value) {
    try {
      delegate.putObject(key, value); // 缓存数据
    } finally {
      releaseLock(key); // 释放锁
    }
  }

  @Override
  public Object getObject(Object key) {
    acquireLock(key); // 获取key对应的锁
    Object value = delegate.getObject(key); // 查询key
    if (value != null) { // 找到 key的缓存项则释放锁
      releaseLock(key);
    }        
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }
  
  private ReentrantLock getLockForKey(Object key) {
    ReentrantLock lock = new ReentrantLock(); // 创建个新的对象
    ReentrantLock previous = locks.putIfAbsent(key, lock); // 尝试添加新对象到locks map中，如果已存在就是用原来的
    return previous == null ? lock : previous;
  }
  
  private void acquireLock(Object key) {
    Lock lock = getLockForKey(key); // 获取 ReentrantLock 对象
    if (timeout > 0) { // 尝试获取锁，如果指定了超时时长，则指定超时时长
      try {
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) { // 如果超时则抛异常
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());  
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      lock.lock(); // 获取锁，没有超时时长，一直阻塞在这里
    }
  }
  
  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    if (lock.isHeldByCurrentThread()) { // 锁是否被当前线程持有
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }  
}