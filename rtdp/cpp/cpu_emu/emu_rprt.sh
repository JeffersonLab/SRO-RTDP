#!/bin/bash
#set -x
# ./emu_rprt <trace_file>

#================= run setup/execution/cleanup =====================
#killall cpu_emu python zmq-event-clnt; 
#t=$(mktemp); tx=$(mktemp); echo $t; echo $tx 
#python launcher_py_cpu_emu_chain.py --components 3 --base-port 7000 --avg-rate 10 --rms 0.1 --duty 0.9 --nic 100 > $t 2> $tx

#killall cpu_emu python zmq-event-clnt
#less $t $tx
#tz=$(mktemp); ./emu_rprt.sh $t 2>$tz
#killall gnuplot_qt; rm /tmp/qtgnuplot*

#==================================basic report =================================
t=$1
set +m
t0a=$(mktemp)
grep "Estimated frame rate" $t|cut -f1,7 -d' ' > $t0a
cat $t0a|gnuplot -p -e "set title 'Sender Estimated frame rate Hz'; stats '$t0a' using 2 nooutput; set yrange [0:STATS_max * 1.2]; p '-' u 1:2 w l" &
echo -n 'Sender Estimated frame rate Hz: '
cut -f2 -d' ' $t0a|get_moments
t0b=$(mktemp)
grep "Estimated bit rate" $t|grep MHz|cut -f1,7 -d' ' > $t0b
cat $t0b|gnuplot -p -e "set title 'Sender Estimated bit rate Mhz';  stats '$t0b' using 2 nooutput; set yrange [0:STATS_max * 1.2]; p '-' u 1:2 w l" &
echo -n 'Sender Estimated bit rate Mhz: '
cut -f2 -d' ' $t0b|get_moments
t0c=$(mktemp)
grep -i "\[emulate_stream:] Sending frame" $t|cut -f 1,7 -d' ' > $t0c
cat $t0c|gnuplot -p -e "set title 'Sender frame size bits';  stats '$t0c' using 2 nooutput; set yrange [0:STATS_max * 1.2]; p '-' u 1:2 w l" &
echo -n "Sender frame size bits: "
cut -f2 -d' ' $t0c|get_moments
#t0d=$(mktemp)
#grep -i "Estimated frame rate" $t|cut -f1,7 -d' ' > $t0d
#cat $t0d|gnuplot -p -e "set title 'Sender Estimated frame rate Hz'; p '-' u 1:2 w l" &
#echo -n "Sender Estimated frame rate Hz: "
#cut -f2 -d' ' $t0d|get_moments
#t0e=$(mktemp)
#grep -i "Estimated bit rate" $t|grep MHz|cut -f1,7 -d' ' > $t0e
#cat $t0e|gnuplot -p -e "set title 'Sender Estimated bit rate MHz'; p '-' u 1:2 w l" &
#echo -n "Sender Estimated bit rate MHz: "
#cut -f2 -d' ' $t0e|get_moments
echo
t0=$(mktemp)
for i in {7003..7001}; do
    grep "cpu_emu ${i}" $t > $t0
    echo "cpu_emu ${i}:"
    echo -n "frame size bits: "; grep -i "Frame size" $t0|grep -v "emulate_stream"|cut -f 9 -d' '|get_moments
    t1=$(mktemp); grep "Measured frame" $t0|grep -v "emulate_stream"|cut -f 1,8 -d' ' > $t1
    gnuplot -p -e "set title '$i Measured frame rate Hz';  stats '$t1' using 2 nooutput; set yrange [0:STATS_max * 1.2]; p '-' u 1:2 w l" < $t1  &
    echo -n "Measured frame rate Hz: "; cut -f 2 -d ' ' $t1 | get_moments 
    t2=$(mktemp); grep "Measured bit" $t0|grep -v "emulate_stream"|cut -f 1,8 -d' '  > $t2
    gnuplot -p -e "set title '$i Measured bit rate MHz';  stats '$t2' using 2 nooutput; set yrange [0:STATS_max * 1.2]; p '-' u 1:2 w l"  < $t2 &
    echo -n "Measured bit rate MHz: "; cut -f 2 -d ' ' $t2 | get_moments
    #grep Compute $t0|cut -f9 -d' '|gnuplot -p -e "set title '$i compute latency nsec'; p '-' w l"
    #grep Compute $t0|cut -f12 -d' '|gnuplot -p -e "set title '$i network latency nsec'; p '-' w l"
    ##grep tsn $t0|cut -f 9,12 -d' '|gnuplot -p -e "set title '$i network latency nsec vs size bits'; p '-' u 1:2"
    #tg=$(mktemp); grep tsn $t0|cut -f 9,12 -d' ' > $tg; gnuplot -p -e "f(x) = a*x + b; fit f(x) '$tg' using 1:2 via a,b; set title '$i network latency nsec vs size bits';  stats '$tg' using 2 nooutput; set yrange [0:STATS_max * 1.2]; p '$tg' u 1:2, f(x) lc 'red'"
  echo
done


t3=$(mktemp)
t4=$(mktemp)
t5=$(mktemp)

#grep "\[cpu_emu 7003\]:  Measured frame rate" $t|cut -f 1,12 -d' '>$t3; grep -i "Estimated frame rate" $t|cut -f 1,9 -d' '>$t4; paste $t3 $t4|tee $t5|gnuplot -p -e "set title 'sent/recd'; p '-' u 1:2 w l lc 'red', '$t5' u 3:4 w l lc 'green'" &

for i in {7003..7001}; do grep $i $t|grep recd|tail -1; done; echo; grep sent $t|tail -1; echo -n "Attempting: "; grep Attempting $t|wc -l; echo -n "dropped: "; grep dropped $t|wc -l

t6=$(mktemp)
t7=$(mktemp)
grep "\[emulate_stream:] Sending frame" $t > $t6
grep "\[cpu_emu 700[123]\]:  frame size" $t >> $t6
grep done $t >> $t6
#grep "Sending frame" $t >> $t6
grep dropped $t >> $t6
sort -k 1 -n $t6 > $t7

t8=$(mktemp)
grep "emulate_stream:] Sending frame" $t7 > $t8
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

#echo -n "cpu_emu 7003: "; grep dropped $t|grep "cpu_emu 7003"|wc -l; echo "frames dropped"
#a=$(echo -n "cpu_emu 7003: "; grep dropped $t|grep "cpu_emu 7003"|wc -l)
echo -n $a; echo " frames dropped"
echo -n "cpu_emu 7002: "; grep dropped $t|grep "cpu_emu 7002"|wc -l; echo "frames dropped"
echo -n "cpu_emu 7001: "; grep dropped $t|grep "cpu_emu 7001"|wc -l; echo "frames dropped"
b=$(grep "emulate_stream:] Sending frame" $t | tail -1)
#echo -n $b
n=$(grep dropped $t|grep "cpu_emu 7003"|wc -l)
d=$(echo -n $b|cut -f 10 -d ' '|sed 's/(//'|sed 's/)//')
#echo $n $d
ratio=$(echo "scale=6; $n / $d" | bc)
echo "Ratio: $ratio"


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

#search for missed frames
#tf=$(mktemp); grep emulate_stream $t|grep -Ev "^\[emulate_stream" |grep -v emulate_sender-zmq > $tf
#grep 700 $t|grep -Ev "^\[cpu_emu"|grep -v launcher_py_cpu_emu|grep -v emulate_stream >> $tf
#sort -n -k1 $tf|grep -v Estimated|grep -v Measured|grep -v recd|grep -v Memory|less
# then compare 7003 to 7002, etc.
