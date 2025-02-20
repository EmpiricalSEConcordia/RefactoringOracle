/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.util.inject;

import org.elasticsearch.util.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/**
 * Produces construction proxies that invoke the class constructor.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class DefaultConstructionProxyFactory<T> implements ConstructionProxyFactory<T> {

  private final InjectionPoint injectionPoint;

  /**
   * @param injectionPoint an injection point whose member is a constructor of {@code T}.
   */
  DefaultConstructionProxyFactory(InjectionPoint injectionPoint) {
    this.injectionPoint = injectionPoint;
  }

  public ConstructionProxy<T> create() {
    @SuppressWarnings("unchecked") // the injection point is for a constructor of T
    final Constructor<T> constructor = (Constructor<T>) injectionPoint.getMember();

    // Use FastConstructor if the constructor is public.
    if (Modifier.isPublic(constructor.getModifiers())) {
    } else {
      constructor.setAccessible(true);
    }

    return new ConstructionProxy<T>() {
      public T newInstance(Object... arguments) throws InvocationTargetException {
        try {
          return constructor.newInstance(arguments);
        } catch (InstantiationException e) {
          throw new AssertionError(e); // shouldn't happen, we know this is a concrete type
        } catch (IllegalAccessException e) {
          throw new AssertionError(e); // a security manager is blocking us, we're hosed
        }
      }
      public InjectionPoint getInjectionPoint() {
        return injectionPoint;
      }
      public Constructor<T> getConstructor() {
        return constructor;
      }
    };
  }
}
