package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.io.BufferedWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

  private final Clock clock;
  private final ProfilingState state = new ProfilingState();
  private final ZonedDateTime startTime;

  @Inject
  ProfilerImpl(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.startTime = ZonedDateTime.now(clock);

  }

  @Override
  public <T> T wrap(Class<T> klass, T delegate) {
    Objects.requireNonNull(klass);
    boolean hasProfiledMethod = Arrays.stream(klass.getMethods())
            .anyMatch(method -> method.isAnnotationPresent(Profiled.class));

    if (!hasProfiledMethod) {
      throw new IllegalArgumentException("The wrapped interface must contain a @Profiled method.");
    }

    ProfilingMethodInterceptor h = new ProfilingMethodInterceptor(clock, delegate, state,startTime);
    Object proxy = Proxy.newProxyInstance(ProfilerImpl.class.getClassLoader(), new Class<?>[]{klass},h);

    return (T) proxy;
  }

  @Override
  public void writeData(Path path) {
    Objects.requireNonNull(path);
    try(BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.APPEND) ){
      writeData(writer);

    } catch (IOException e){
      e.printStackTrace();
    }
    // TODO: Write the ProfilingState data to the given file path. If a file already exists at that
    //       path, the new data should be appended to the existing file.
  }

  @Override
  public void writeData(Writer writer) throws IOException {
    writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
    writer.write(System.lineSeparator());
    state.write(writer);
    writer.write(System.lineSeparator());
  }
}
