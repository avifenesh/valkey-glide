#!/bin/bash
rm -f results.txt

for i in {1..3}; do
  for j in {1..25}; do
    node index.mjs >> results.txt &
  done
  wait
done

awk '{total+=$1; count+=1} END {printf "%.3f\n", total/count}' results.txt
