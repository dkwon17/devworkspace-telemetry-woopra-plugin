/*
 * Copyright (c) 2020-2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.devworkspace.services.telemetry.woopra.graalvm;

import com.google.common.collect.Streams;
import com.oracle.svm.core.annotate.AutomaticFeature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This runs at native-image generation time, and registers all retrofit.http and com.segment.analytics fields,
 * methods, constructors, classes, and super classes for reflection in GraalVM.
 *
 * Native Image has partial support for reflection and needs to know ahead-of-time the reflectively
 * accessed program elements.  See https://www.graalvm.org/reference-manual/native-image/Reflection/
 */
@AutomaticFeature
public class RuntimeReflectionRegistrationFeature
        extends org.eclipse.che.incubator.workspace.telemetry.graalvm.RuntimeReflectionRegistrationFeature {

  private final String RETROFIT_HTTP = "retrofit.http";
  private final String COM_SEGMENT_ANALYTICS = "com.segment.analytics";

  public void beforeAnalysis(BeforeAnalysisAccess access) {
    for (String prefix : Arrays.asList(
            RETROFIT_HTTP,
            COM_SEGMENT_ANALYTICS)) {
      Reflections reflections = new Reflections(prefix, new SubTypesScanner(false));
      Streams.concat(
              reflections.getSubTypesOf(Object.class).stream(),
              reflections.getSubTypesOf(Enum.class).stream()
      ).forEach(this::registerFully);
    }
  }

  private Set<Class<?>> classesAlreadyRegistered = new HashSet<>();
  private Set<Type> typesAlreadyRegistered = new HashSet<>();

  protected void registerFully(Type type) {
    if (typesAlreadyRegistered.contains(type)) {
      return;
    }
    typesAlreadyRegistered.add(type);
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      registerFully(parameterizedType.getRawType());
      for (Type paramType : parameterizedType.getActualTypeArguments()) {
        registerFully(paramType);
      }
    } else if (type instanceof GenericArrayType) {
      GenericArrayType genericArrayType = (GenericArrayType) type;
      registerFully(genericArrayType.getGenericComponentType());
    } else if (type instanceof Class<?>) {
      registerFully((Class<?>) type);
    }
  }

  private void registerFully(Class<?> clazz) {
    if (classesAlreadyRegistered.contains(clazz)) {
      return;
    }
    if (clazz.getPackage() == null || clazz.getPackage().getName() == null || clazz.getPackage().getName().startsWith("java")) {
      return;
    }
    System.out.println("    =>  Registering class: " + clazz.getName());
    RuntimeReflection.register(clazz);
    classesAlreadyRegistered.add(clazz);
    for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
      RuntimeReflection.register(constructor);
    }
    for (Method method : clazz.getDeclaredMethods()) {
      RuntimeReflection.register(method);
    }
    for (Field field : clazz.getDeclaredFields()) {
      RuntimeReflection.register(true, field);
      registerFully(field.getGenericType());
    }
    for (Class<?> memberClass : clazz.getDeclaredClasses()) {
      registerFully(memberClass);
    }
    Class<?> superClass = clazz.getSuperclass();
    if (superClass != null) {
      registerFully(superClass);
    }
    Class<?> enclosingClass = clazz.getEnclosingClass();
    if (enclosingClass != null) {
      registerFully(enclosingClass);
    }
  }
}
