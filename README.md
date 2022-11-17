MyBatis 3.4.2 源码学习
=====================================

![mybatis](http://mybatis.github.io/images/mybatis-logo.png)

#### 一级缓存

在 BaseExecutor.query() 方法中，会根据 `<select>` 标签的 flushCache 和 Configuration 的 localCacheScope 决定是否清空一级缓存；
在update() 方法中，负责执行 insert、update、delete这三类语句也会清空缓存。

#### 二级缓存

1. mybatis-config.xml <settings>中配置 cacheEnabled，默认值为true
2. mapper.xml 中配置了 <cache> 节点会根据 namespace 创建相应的Cache对象，<cache-ref namespace=""> 节点不会创建独立的 Cache对象，它和指定的 namespace共享同一个。
3. `<select>` 节点中的 useCache属性，查询的结果会保存到二级缓存中。useCache默认值为true。

Essentials
----------

* [See the docs](http://mybatis.github.io/mybatis-3)
* [Download Latest](https://github.com/mybatis/mybatis-3/releases)
* [Download Snapshot](https://oss.sonatype.org/content/repositories/snapshots/org/mybatis/mybatis/)
