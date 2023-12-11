#!/bin/bash
${JAVA_HOME}/bin/java -Xmx30g -Xms30g -Dsolr.install.dir=/home/gus/projects/gus-asf/solr/testing/lw_exitable/solr-10.0.0-SNAPSHOT \
   -Dfile.encoding=UTF-8 -jar /home/gus/projects/gus-asf/solr/code/solr-exitable-dr-jmh/build/libs/test_dep-1.0-SNAPSHOT-unojar.jar \
   ../../testing/testidx/ solr-perf_shard1_replica_n1 $JAVA_HOME &
taskset -cp 10 $!

