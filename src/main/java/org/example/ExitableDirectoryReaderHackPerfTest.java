/*
 * Copyright 2023 Patrick G. Heck
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

package org.example;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.*;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.RefCounted;


import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Non JMH Testing class meant to eliminate as much complexity as possible. Not using test harness to avoid needing
 * to defeat/control randomization and minimize background management tasks that the embedded solr server class might\
 * initiate. Also, I wanted to be able to point it at an arbitrary solr core.
 * <p>
 * This should be replaced with a rigorous JMH benchmark. This is an ugly first approximation microbenchmark for
 * exploratory, sniff test purposes only.
 */
@SuppressWarnings("SameParameterValue")
public class ExitableDirectoryReaderHackPerfTest {

    public static final Integer TERM_FILE_SIZE = 1_000_000;
    public static final int WARM_ROUNDS = 1000;
    public static final int ROUNDS = 1000;
    public static final int JIT_REST = 50; // ms
    public static final int TERMS_PER_ROUND = 1000;

    public static void main(String[] args) {
        String descriptor = Instant.now() + "-" + WARM_ROUNDS + "-" + ROUNDS + "-" +
                System.getProperty("solr.useExitableDirectoryReader");

        // This identifies the key parameters of the test.
        System.out.println(descriptor);

        try {
            // Also you will need to set -Dsolr.install.dir=<SOLR_INSTALL>
            // and optionally set -Dsolr.useExitableDirectoryReader=true
            Path solrHome = Path.of(args[0]);
            String coreName = args[1];
            CoreContainer coreContainer = new CoreContainer(solrHome, null);
            coreContainer.load();
            Thread.sleep(10_000);
            SolrCore solrCore = coreContainer.getCore(coreName);
            if (solrCore == null) {
                throw new RuntimeException("Could not find core:" + coreName);
            }
            perfTestQueries(solrCore, descriptor);
            perfTestFacets(solrCore, descriptor);
            coreContainer.shutdown();
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        } finally {
            System.exit(0); // something not stopping despite shutdown call really don't want to figure it out
        }
    }

    private static void perfTestFacets(SolrCore solrCore, String descriptor) {
        //TODO: loop through terms, collect timings, etc

        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.set(CommonParams.Q, "*:*");
        solrParams.set("facet", "true");
        solrParams.set("facet.field", "randomLabel");
        LocalSolrQueryRequest localSolrQueryRequest = new LocalSolrQueryRequest(solrCore, solrParams);

        SolrQueryResponse rsp = new SolrQueryResponse();
        PluginBag<SolrRequestHandler> requestHandlers = solrCore.getRequestHandlers();
        System.out.println(requestHandlers.keySet());

        solrCore.execute(solrCore.getRequestHandler("/select"), localSolrQueryRequest, rsp);

        System.out.println(rsp.getValues());
    }

    private static void perfTestQueries(SolrCore solrCore, String descriptor) throws IOException, InterruptedException, ExecutionException {
        // for accurate comparison and faster testing write down our queries and re-use them.
        File queryFile = ensureTermsFile(solrCore);
        ArrayList<TermQuery> queryTerms = loadTermsFile(queryFile);
        ArrayList<Long> timings = new ArrayList<>();
        // throw away 100 runs as warmup
        RolingAverage ra = new RolingAverage(20);
        SolrIndexSearcher indexSearcher = getSolrIndexSearcher(solrCore);
        warmUpQTerms(WARM_ROUNDS, queryTerms, indexSearcher, ra, descriptor);
        ra.clear();
        // Now we get to the part we care about, run our test 100 times to develop stats
        // regarding performance. We skip the terms already used by our warmup
        testQTerms(ROUNDS, WARM_ROUNDS + 1, queryTerms, indexSearcher,
                timings, ra, descriptor);

        LongSummaryStatistics stats = timings.stream().collect(Collectors.summarizingLong(x -> x));
        System.out.println(stats);
    }

    private static SolrIndexSearcher getSolrIndexSearcher(SolrCore solrCore) throws InterruptedException, ExecutionException {
        Future<Void>[] waitSearcher = (Future<Void>[]) Array.newInstance(Future.class, 1);
        RefCounted<SolrIndexSearcher> searcher = solrCore.getSearcher(true, true, waitSearcher);
        waitSearcher[0].get();
        return searcher.get();
    }

    private static void testQTerms(int rounds, int startTerm, ArrayList<TermQuery> queryTerms, SolrIndexSearcher indexSearcher, ArrayList<Long> timings, RolingAverage ra, String descriptor) throws IOException {
        System.out.println("START TEST");
        PrintWriter pw = new PrintWriter(new FileWriter(descriptor + "-data.txt"));
        // AMD Ryzen Threadripper 1950X (2185.537 MHZ, not overclocked)
        for (int i = startTerm; i < rounds + startTerm; i++) {
            long timing = queryEachOnce(queryTerms, indexSearcher);
            timings.add(timing);
            pw.println(timing);
            ra.printNext(timing, i);
        }
        pw.close();
    }

    private static void warmUpQTerms(int rounds, ArrayList<TermQuery> queryTerms, SolrIndexSearcher indexSearcher, RolingAverage ra, String descriptor) throws IOException, InterruptedException {
        PrintWriter pw = new PrintWriter(new FileWriter(descriptor + "-warming.txt"));        //now take measurements. Each iteration is taking around 0.4s on an
        for (int i = 0; i < rounds; i++) {
            long timing = queryEachOnce(queryTerms, indexSearcher);
            Thread.sleep(JIT_REST); // let JIT do some work.
            pw.println(timing);
            ra.printNext(timing, i);
        }
        pw.close();
    }

    private static ArrayList<TermQuery> loadTermsFile(File queryFile) throws IOException {
        System.out.println("Loading terms from " + queryFile);
        BufferedReader reader = new BufferedReader(new FileReader(queryFile));
        ArrayList<TermQuery> queryTerms = new ArrayList<>(TERMS_PER_ROUND);
        int i= 0;
        while (reader.ready() && i++ < TERMS_PER_ROUND ) {
            queryTerms.add(new TermQuery(new Term("body", reader.readLine())));
        }
        reader.close();
        return queryTerms;
    }

    private static File ensureTermsFile(SolrCore solrCore) throws IOException {
        File queryFile = new File("queries.txt");
        if (!queryFile.exists()) {
            // Find a set of terms we can generate random queries from

            System.out.println("Terms file not found, generating new one with " + TERM_FILE_SIZE + " terms");

            ModifiableSolrParams solrParams = new ModifiableSolrParams();
            solrParams.set(CommonParams.QT, "/admin/luke");
            solrParams.set(CommonParams.FL, "body");
            solrParams.set("numTerms", TERM_FILE_SIZE);

            LocalSolrQueryRequest localSolrQueryRequest = new LocalSolrQueryRequest(solrCore, solrParams);

            SolrQueryResponse rsp = new SolrQueryResponse();
            PluginBag<SolrRequestHandler> requestHandlers = solrCore.getRequestHandlers();
            System.out.println(requestHandlers.keySet());

            // this query takes about 37 seconds, which is the reason we write down the results in queries.txt
            // but unlike a facet query it does not take infinity and run the JVM out of memory :)
            solrCore.execute(solrCore.getRequestHandler("/admin/luke"), localSolrQueryRequest, rsp);

            System.out.println(rsp.getResponseHeader());
            System.out.println(rsp);
            System.out.println(rsp.getValues());

            NamedList<?> fields = (NamedList<?>) rsp.getValues().get("fields");
            NamedList<?> body = (NamedList<?>) fields.get("body");
            NamedList<?> topterms = (NamedList<?>) body.get("topTerms");

            Pattern numeric = Pattern.compile("\\d+");
            ArrayList<String> terms = new ArrayList<>();
            int ignoreFirst = 25;
            for (Object topterm : topterms) {
                @SuppressWarnings("unchecked")
                String key = ((Map.Entry<String, String>) topterm).getKey();
                // avoid most stopwords, and super common words
                if (ignoreFirst <= 0 && key.length() > 3 && !numeric.matcher(key).find()) {
                    terms.add(key);
                }
                ignoreFirst--;
            }
            System.out.println(terms);
            System.out.printf("NOTE: creating terms file of size %s from %s terms\n", TERM_FILE_SIZE, terms.size());

            // reduce chance of drift due to changing term frequencies
            Collections.shuffle(terms);
            // now write out the top 1000 terms
            Random random = new Random();
            PrintWriter pw = new PrintWriter(new FileWriter(queryFile));
            for (int i = 0; i < TERM_FILE_SIZE; i++) {
                pw.println(terms.get(random.nextInt(terms.size())));
            }
            pw.close();
        }
        return queryFile;
    }

    private static long queryEachOnce(ArrayList<TermQuery> queryTerms, SolrIndexSearcher indexSearcher) throws IOException {
        long start = System.nanoTime();
        for (TermQuery queryTerm : queryTerms) {
            indexSearcher.search(queryTerm, 1);
        }
        long end = System.nanoTime();
        return end - start;
    }

    static class RolingAverage {
        private final int span;
        private final ArrayList<Long> lastN;
        private int pos = 0;

        RolingAverage(int span) {
            this.span = span;
            this.lastN = new ArrayList<>(span);
            for (int i = 0; i < span; i++) {
                lastN.add(null);
            }
            System.out.println(lastN.size());
        }

        void printNext(long datum, int round) {
            pos = pos == span ? 0 : pos;
            lastN.set(pos++, datum);
            LongSummaryStatistics stats = lastN.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.summarizingLong(x -> x));
            System.out.println(stats + " " + round);
        }

        void clear() {
            for (int i = 0; i < span; i++) {
                lastN.add(null);
            }
        }
    }
}
