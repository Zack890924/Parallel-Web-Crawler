package com.udacity.webcrawler.profiler;

import java.awt.font.TextHitInfo;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;


/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object delegate;
  private final ProfilingState state;
  private final ZonedDateTime startTime;

  ProfilingMethodInterceptor(Clock clock, Object delegate, ProfilingState state, ZonedDateTime startTime) {

    this.clock = Objects.requireNonNull(clock);
    this.delegate = delegate;
    this.state = state;
    this.startTime = startTime;

  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable{
    Profiled profiledAnnotation = method.getAnnotation(Profiled.class);
    Object invoked = null;
    Instant startTime = null;

    if (profiledAnnotation != null){
      startTime = clock.instant();
    }
    try {
      invoked = method.invoke(delegate,args);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    } catch (IllegalAccessException e){
      throw new RuntimeException(e);
    }
    finally {
      if (profiledAnnotation!=null){
      Duration duration = Duration.between(startTime, clock.instant());
      state.record(delegate.getClass(), method, duration);}
    }

    return invoked;
  }

}
