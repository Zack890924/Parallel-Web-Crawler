package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final List<Pattern> ignoredUrls;
  private final int maxDepth;
  private final PageParserFactory parserFactory;

  @Inject
  ParallelWebCrawler(
          Clock clock,
          PageParserFactory parserFactory,
          @Timeout Duration timeout,
          @PopularWordCount int popularWordCount,
          @MaxDepth int maxDepth,
          @IgnoredUrls List<Pattern> ignoredUrls,
          @TargetParallelism int threadCount) {
    this.clock = clock;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.ignoredUrls = ignoredUrls;
    this.maxDepth = maxDepth;
    this.parserFactory = parserFactory;
  }


  @Override
  public CrawlResult crawl(List<String> startingUrls){
    Instant deadline = clock.instant().plus(timeout);
    ConcurrentHashMap<String , Integer> wordCounts = new ConcurrentHashMap<>();
    ConcurrentHashMap<String , Boolean> urlsVisited = new ConcurrentHashMap<>();

    for (String url: startingUrls){
      pool.invoke(new crawlInternal(url, deadline, maxDepth, wordCounts, urlsVisited));
    }
    if (wordCounts.isEmpty()){
      return new CrawlResult.Builder().setWordCounts(wordCounts).setUrlsVisited(urlsVisited.keySet().size()).build();
    }

    return new CrawlResult.Builder().setWordCounts(WordCounts.sort(wordCounts, popularWordCount)).setUrlsVisited(urlsVisited.keySet().size()).build();
  }

  public class crawlInternal extends RecursiveTask<Long>{
    private String url;
    private Instant deadline;
    private int maxDepth;
    private ConcurrentHashMap<String, Integer> wordCounts;
    private ConcurrentHashMap<String, Boolean> urlsVisited;

    private crawlInternal(String url, Instant deadline, int maxDepth, ConcurrentHashMap wordCounts, ConcurrentHashMap urlsVisited)
    {
      this.url = url;
      this.deadline = deadline;
      this.wordCounts = wordCounts;
      this.maxDepth = maxDepth;
      this.urlsVisited = urlsVisited;
    }
    @Override
    protected Long compute(){
      if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
        return 0L;
      }
      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(url).matches()) {
          return 0L;
        }
      }
//      if (urlsVisited.contains(url)) {
//        return 0L;
//      }
      if (urlsVisited.putIfAbsent(url, true) != null) {
        return 0L;
      }
      PageParser.Result result = parserFactory.get(url).parse();
      for (ConcurrentHashMap.Entry<String, Integer> e : result.getWordCounts().entrySet()){
        wordCounts.compute(e.getKey(), (k,v) -> (v==null) ? e.getValue() : e.getValue() + v);
      }
      List<crawlInternal> subTasks = new ArrayList<>();
      for (String link: result.getLinks()){
        subTasks.add(new crawlInternal(link, deadline, maxDepth-1, wordCounts, urlsVisited));
      }
      invokeAll(subTasks);
      return 1L;
    }
  }


  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }
}
