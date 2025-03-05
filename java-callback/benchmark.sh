#/bin/bash

CLIENT=$1

if [ -z $1 ]; then
  CLIENT="callback"
fi

case $CLIENT in
  "callback")
    bash ./gradlew :benchmarks:run '-PmainClass=glide.benchmarks.BenchmarkingApp' -x protobuf --args='--host localhost --port 6381 --concurrentTasks "[1 10 100]" --dataSize 100 --clients Glide_Callback --resultsFile /tmp/glide-callback.output'
  ;;
  "glide")
    bash ./gradlew :benchmarks:run '-PmainClass=glide.benchmarks.BenchmarkingApp' -x protobuf --args='--host localhost --port 6381 --concurrentTasks "[1 10 100]" --dataSize 100 --clients Glide --resultsFile /tmp/glide-socket.output'
  ;;
  "lettuce")
    bash ./gradlew :benchmarks:run '-PmainClass=glide.benchmarks.BenchmarkingApp' -x protobuf --args='--host localhost --port 6381 --concurrentTasks "[1 10 100]" --dataSize 100 --clients lettuce --resultsFile /tmp/lettuce.output'
  ;;
  "jedis")
    bash ./gradlew :benchmarks:run '-PmainClass=glide.benchmarks.BenchmarkingApp' -x protobuf --args='--host localhost --port 6381 --concurrentTasks "[1 10 100]" --dataSize 100 --clients jedis --resultsFile /tmp/jedis.output'
  ;;
esac
