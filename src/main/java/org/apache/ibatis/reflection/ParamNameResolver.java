/**
 *    Copyright 2009-2016 the original author or authors.
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

package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

public class ParamNameResolver {

  private static final String GENERIC_NAME_PREFIX = "param";
  private static final String PARAMETER_CLASS = "java.lang.reflect.Parameter";
  private static Method GET_NAME;
  private static Method GET_PARAMS;

  static {
    try {
      Class<?> paramClass = Resources.classForName(PARAMETER_CLASS);
      GET_NAME = paramClass.getMethod("getName");
      GET_PARAMS = Method.class.getMethod("getParameters");
    } catch (Exception e) {
      // ignore
    }
  }

  /**
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  // 存储方法参数 key 为 参数index，value为参数名称
  private final SortedMap<Integer, String> names;

  // 方法上是否有 @Param 注解
  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    final Class<?>[] paramTypes = method.getParameterTypes(); // 获取方法所有参数的类型
    final Annotation[][] paramAnnotations = method.getParameterAnnotations(); // 获取每个参数上的注解
    final SortedMap<Integer, String> map = new TreeMap<Integer, String>();
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      if (isSpecialParameter(paramTypes[paramIndex])) { // 如果参数类型是 RowBounds 或者 ResultHandler 则跳过
        // skip special parameters
        continue;
      }
      String name = null;
      // 遍历所有参数上的注解
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) { // 只要有 @Param ， 就将 hasParamAnnotation 设置为 true
          hasParamAnnotation = true;
          name = ((Param) annotation).value(); // 获取 @Param 的 value值
          break;
        }
      }
      if (name == null) {
        // @Param was not specified.
        // 该参数没有对应的 @Param 注解，则根据配置决定是否使用参数的实际名称作为名称
        if (config.isUseActualParamName()) {
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) { // 使用参数索引作为名称
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name); // 缓存到map中
    }
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    if (GET_PARAMS == null) {
      return null;
    }
    try {
      Object[] params = (Object[]) GET_PARAMS.invoke(method);
      return (String) GET_NAME.invoke(params[paramIndex]);
    } catch (Exception e) {
      throw new ReflectionException("Error occurred when invoking Method#getParameters().", e);
    }
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name.<br />
   * Multiple parameters are named using the naming rule.<br />
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    if (args == null || paramCount == 0) { // 无参数，返回null
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) { // 没有 @Param 注解，并且只有一个参数
      return args[names.firstKey()];
    } else { // 处理使用 @Param 注解指定了参数名或有多个参数的情况
      final Map<String, Object> param = new ParamMap<Object>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        // 将参数名和实参对应关系保存起来
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        // 生成默认参数名称，param1，param2...
        final String genericParamName = GENERIC_NAME_PREFIX + String.valueOf(i + 1);
        // ensure not to overwrite parameter named with @Param
        // 如果 @Param 注解指定的参数名就是 param + 索引格式的，则不需要添加
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
}
