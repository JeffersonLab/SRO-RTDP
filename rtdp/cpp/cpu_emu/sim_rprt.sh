#!/bin/bash
#set -x
# ./sim_rprt <trace_file>
#==================================basic report =================================
t=$1
set +m
grep "Estimated chunk rate" $t|cut -f1,7 -d' '|gnuplot -p -e "set title 'Sender Estimated chunk rate Hz'; p '-' u 1:2 w l" &
grep "Estimated bit rate" $t|grep MHz|cut -f1,7 -d' '|gnuplot -p -e "set title 'Sender Estimated bit rate Mhz'; p '-' u 1:2 w l" &
echo -n "Sender chunk size bits: "; grep -i "Sending chunk" $t|cut -f 7 -d' '|get_moments
echo -n "Sender Estimated chunk rate Hz: "; grep -i "Estimated chunk rate" $t|cut -f7 -d' '|get_moments
echo -n "Sender Estimated bit rate MHz: "; grep -i "Estimated bit rate" $t|grep MHz|cut -f7 -d' '|get_moments
echo
t0=$(mktemp)
for i in {7003..7001}; do
    grep "cpu_emu ${i}" $t > $t0
    echo "cpu_emu ${i}:"
    echo -n "chunk size bits: "; grep -i "Chunk size" $t0|grep -v "simulate_stream"|cut -f 9 -d' '|get_moments
    t1=$(mktemp); grep "Measured chunk" $t0|grep -v "simulate_stream"|cut -f 1,8 -d' ' > $t1
    gnuplot -p -e "set title '$i Measured chunk rate Hz'; p '-' u 1:2 w l" < $t1  &
    echo -n "Measured chunk rate Hz: "; cut -f 2 -d ' ' $t1 | get_moments 
    t2=$(mktemp); grep "Measured bit" $t0|grep -v "simulate_stream"|cut -f 1,8 -d' '  > $t2
    gnuplot -p -e "set title '$i Measured bit rate MHz'; p '-' u 1:2 w l"  < $t2 &
    echo -n "Measured bit rate MHz: "; cut -f 2 -d ' ' $t2 | get_moments
    grep Compute $t0|cut -f9 -d' '|gnuplot -p -e "set title '$i compute latency nsec'; p '-' w l"
    grep Compute $t0|cut -f12 -d' '|gnuplot -p -e "set title '$i network latency nsec'; p '-' w l"
    #grep tsn $t0|cut -f 9,12 -d' '|gnuplot -p -e "set title '$i network latency nsec vs size bits'; p '-' u 1:2"
    tg=$(mktemp); grep tsn $t0|cut -f 9,12 -d' ' > $tg; gnuplot -p -e "f(x) = a*x + b; fit f(x) '$tg' using 1:2 via a,b; set title '$i network latency nsec vs size bits'; p '$tg' u 1:2, f(x) lc 'red'"
  echo
done


t3=$(mktemp)
t4=$(mktemp)
t5=$(mktemp)

grep "\[cpu_emu 7003\]:  Measured chunk rate" $t|cut -f 1,12 -d' '>$t3; grep -i "Estimated chunk rate" $t|cut -f 1,9 -d' '>$t4; paste $t3 $t4|tee $t5|gnuplot -p -e "set title 'sent/recd'; p '-' u 1:2 w l lc 'red', '$t5' u 3:4 w l lc 'green'" &

for i in {7003..7001}; do grep $i $t|grep recd|tail -1; done; echo; grep sent $t|tail -1; echo -n "Attempting: "; grep Attempting $t|wc -l; echo -n "dropped: "; grep dropped $t|wc -l

t6=$(mktemp)
t7=$(mktemp)
grep "\[simulate_stream:] Sending chunk" $t > $t6
grep "\[cpu_emu 700[123]\]:  chunk size" $t >> $t6
grep done $t >> $t6
#grep "Sending chunk" $t >> $t6
grep dropped $t >> $t6
sort -k 1 -n $t6 > $t7

t8=$(mktemp)
grep "simulate_stream:] Sending chunk" $t7 > $t8
grep "cpu_emu 7003" $t7|grep done >> $t8
t9=$(mktemp)
sort -k 1 -n $t8 > $t9

t10=$(mktemp)
grep "cpu_emu 7003]:  Sending" $t7 > $t10
grep "cpu_emu 7002" $t7|grep done >> $t10
t11=$(mktemp)
sort -k 1 -n $t10 > $t11

t12=$(mktemp)
grep "cpu_emu 7002]:  Sending" $t7 > $t12
grep "cpu_emu 7001" $t7|grep done >> $t12
t13=$(mktemp)
sort -k 1 -n $t12 > $t13

echo -n "cpu_emu 7003: "; grep dropped $t|grep "cpu_emu 7003"|wc -l; echo "chunks dropped"
echo -n "cpu_emu 7003: "; grep dropped $t|grep "cpu_emu 7002"|wc -l; echo "chunks dropped"
echo -n "cpu_emu 7003: "; grep dropped $t|grep "cpu_emu 7001"|wc -l; echo "chunks dropped"
tail -1 $t

echo "t $t"
echo "t0 $t0"
echo "t1 $t1"
echo "t2 $t2"
echo "t3 $t3"
echo "t4 $t4"
echo "t5 $t5"
echo "t6 $t6"
echo "t7 $t7"
echo "t8 $t8"
echo "t9 $t9"
echo "t10 $t10"
echo "t11 $t11"
echo "t12 $t12"
echo "t13 $t13"

set -m
