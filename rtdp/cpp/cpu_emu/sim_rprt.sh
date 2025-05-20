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
  echo
done


grep "\[cpu_emu 7003\]:  Measured chunk rate" $t|cut -f 1,12 -d' '>/tmp/junk0; grep -i "Estimated chunk rate" $t|cut -f 1,9 -d' '>/tmp/junk1; paste /tmp/junk[01]|tee /tmp/junk3|gnuplot -p -e "set title 'sent/recd'; p '-' u 1:2 w l lc 'red', '/tmp/junk3' u 3:4 w l lc 'green'" &

for i in {7003..7001}; do grep $i $t|grep recd|tail -1; done; echo; grep sent $t|tail -1; echo -n "Attempting: "; grep Attempting $t|wc -l; echo -n "dropped: "; grep dropped $t|wc -l

echo "t0 $t0"
echo "t1 $t1"
echo "t2 $t2"
echo "t3 $t3"

set -m
