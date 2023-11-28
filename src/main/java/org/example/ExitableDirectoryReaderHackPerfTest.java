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
import java.util.*;
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
public class ExitableDirectoryReaderHackPerfTest {

    public static void main(String[] args) {
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
            @SuppressWarnings("unchecked")
            Future<Void>[] waitSearcher = (Future<Void>[]) Array.newInstance(Future.class, 1);
            RefCounted<SolrIndexSearcher> searcher = solrCore.getSearcher(true, true, waitSearcher);
            waitSearcher[0].get();

            SolrIndexSearcher indexSearcher = searcher.get();

            // for accurate comparison write down our queries and re-use them.
            File queryFile = new File("queries.txt");
            if (!queryFile.exists()) {
                // Find a set of terms we can generate random queries from

                ModifiableSolrParams solrParams = new ModifiableSolrParams();
                solrParams.set(CommonParams.QT, "/admin/luke");
                solrParams.set(CommonParams.FL, "body");
                solrParams.set("numTerms", "5000");

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

                // now write out the top 1000 terms
                Random random = new Random();
                PrintWriter pw = new PrintWriter(new FileWriter(queryFile));
                for (int i = 0; i < 1000; i++) {
                    pw.println(terms.get(random.nextInt(terms.size())));
                }
                pw.close();
            }

            // Now load the file
            BufferedReader reader = new BufferedReader(new FileReader(queryFile));
            ArrayList<TermQuery> queryTerms = new ArrayList<>(1000);
            while (reader.ready()) {
                queryTerms.add(new TermQuery(new Term("body", reader.readLine())));
            }

            ArrayList<Long> timings = new ArrayList<>();
            // Ok so now we get to the part we care about, run our test 100 times to develop stats regarding performance

            // throw away 2 runs as warmup
            queryEachOnce(queryTerms, indexSearcher);
            queryEachOnce(queryTerms, indexSearcher);

            //now take measurements. Each iteration is taking around 0.4s on an
            // AMD Ryzen Threadripper 1950X (2185.537 MHZ, not overclocked)
            for (int i = 0; i < 1000; i++) {
                long timing = queryEachOnce(queryTerms, indexSearcher);
                timings.add(timing);
            }

            LongSummaryStatistics stats = timings.stream().collect(Collectors.summarizingLong(x -> x));
            System.out.println(stats);
            coreContainer.shutdown();
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        } finally {
            System.exit(0); // something not stopping despite shutdown call really don't want to figure it out
        }
    }

    private static long queryEachOnce(ArrayList<TermQuery> queryTerms, SolrIndexSearcher indexSearcher) throws IOException {
        long start = System.nanoTime();
        for (TermQuery queryTerm : queryTerms) {
            indexSearcher.search(queryTerm, 1);
        }
        long end = System.nanoTime();
        return end - start;
    }
}
