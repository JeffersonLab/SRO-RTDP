#!/bin/bash
#set -x
# ./sim_rprt <trace_file> <num_components> <base_port>
num_cmp=$2
base_port=$3
hi_port=$(echo "$base_port + $num_cmp - 1" | bc)
echo "num_cmp = $num_cmp"
echo "base_port = $base_port"
echo "hi_port = $hi_port"

#================= run setup/execution/cleanup =====================
#killall cpu_sim python zmq-event-clnt; 
#t=$(mktemp); tx=$(mktemp); echo $t; echo $tx 
#python launcher_py_cpu_sim_chain.py --components 3 --base-port 7000 --avg-rate 10 --rms 0.1 --duty 0.9 --nic 100 > $t 2> $tx

#killall cpu_sim python zmq-event-clnt
#less $t $tx
#tz=$(mktemp); ./sim_rprt.sh $t 2>$tz
#killall gnuplot_qt; rm /tmp/qtgnuplot*

#==================================basic report =================================
t=$1
set +m
t0a=$(mktemp); echo "t0a $t0a"
grep "Estimated frame rate" $t|cut -f1,7 -d' ' > $t0a
cat $t0a|gnuplot -p -e "unset key; set title 'Sender Estimated frame rate'; stats '$t0a' using 2 nooutput; set xlabel 'Clock (hr)'; set ylabel 'Hz'; p '-' u ((\$1/1e6)/3.6e3):2 w l" &
echo -n 'Sender Estimated frame rate Hz: '
cut -f2 -d' ' $t0a|get_moments
t0b=$(mktemp); echo "t0b $t0b"
grep "Estimated bit rate" $t|grep MHz|cut -f1,7 -d' ' > $t0b
cat $t0b|gnuplot -p -e "unset key; set title 'Sender Estimated bit rate';  stats '$t0b' using (\$2/1e6) nooutput; set xlabel 'Clock (hr)'; set ylabel 'Mbps'; p '-' u ((\$1/1e6)/3.6e3):(\$2/1e0) w l" &
echo -n 'Sender Estimated bit rate Mhz: '
cut -f2 -d' ' $t0b|get_moments
t0c=$(mktemp); echo "t0c $t0c"
grep -i "\[simulate_stream:] Sending frame" $t|cut -f 1,7 -d' ' > $t0c
cat $t0c|gnuplot -p -e "set xlabel 'Clock (hr)'; set ylabel 'Mb'; unset key; set title 'Sender frame size';  stats '$t0c' using (\$2/1e6) nooutput; p '-' u ((\$1/1e6)/3.6e3):(\$2/1e6) w l" &
echo -n "Sender frame size bits: "
cut -f2 -d' ' $t0c|get_moments
echo
t0=$(mktemp); echo "t0 $t0"
for i in $(seq "$base_port" "$hi_port"); do
    grep "cpu_sim ${i}" $t > $t0
    echo "Component $(echo "$i - $base_port + 1" | bc):"
    echo -n "frame size bits: "; grep -i "Frame size" $t0|grep -v "simulate_stream"|grep -v Sending|cut -f 9 -d' '|get_moments
    t1=$(mktemp); echo "t1 $t1"; grep "Measured frame" $t0|grep -v "simulate_stream"|cut -f 1,8 -d' ' > $t1
    gnuplot -p -e "set xlabel 'Clock (hr)'; set ylabel 'Hz'; unset key; set title 'Component $(echo "$i - $base_port + 1" | bc) Measured frame rate';  stats '$t1' using 2 nooutput; p '-' u ((\$1/1e6)/3.6e3):2 w l" < $t1  &
    echo -n "Measured frame rate Hz: "; cut -f 2 -d ' ' $t1 | get_moments 
    t2=$(mktemp); echo "t2 $t2"; grep "Measured bit" $t0|grep -v "simulate_stream"|cut -f 1,8 -d' '  > $t2
    gnuplot -p -e "set xlabel 'Clock (hr)'; set ylabel 'Mbps'; unset key; set title 'Component $(echo "$i - $base_port + 1" | bc) Measured bit rate';  stats '$t2' using 2 nooutput; p '-' u ((\$1/1e6)/3.6e3):2 w l"  < $t2 &
    echo -n "Measured bit rate MHz: "; cut -f 2 -d ' ' $t2 | get_moments
    grep Compute $t0|cut -f1,9 -d' '| gnuplot -p -e "set xlabel 'Clock (hr)'; set ylabel 'msec'; unset key; set title 'Component $(echo "$i - $base_port + 1" | bc) compute latency'; p '-' u ((\$1/1e6)/3.6e3):(\$2/1e3) w l"
    grep Compute $t0|cut -f1,12 -d' '|gnuplot -p -e "set xlabel 'Clock (hr)'; set ylabel 'usec'; unset key; set title 'Component $(echo "$i - $base_port + 1" | bc) network latency'; p '-' u ((\$1/1e6)/3.6e3):2 w l"
    #grep tsn $t0|cut -f 9,12 -d' '|gnuplot -p -e "unset key; set title 'Component $(echo "$i - $base_port + 1" | bc) network latency usec vs size bits'; p '-' u ((\$1/1e6)/3.6e3):2"
    #tg=$(mktemp); grep tsn $t0|cut -f 9,12 -d' ' > $tg; gnuplot -p -e "unset key; f(x) = a*x + b; fit f(x) '$tg' using 1:2 via a,b; set title 'Component $(echo "$i - $base_port + 1" | bc) network latency usec vs size bits';  stats '$tg' using 2 nooutput; p '$tg' u 1:2, f(x) lc 'red'"
    #tg=$(mktemp); grep tsn $t0|cut -f 9,12 -d' ' > $tg; gnuplot -p -e "unset key; f(x) = a*x + b; fit f(x) '$tg' using 1:2 via a,b; set title 'Component $(echo "$i - $base_port + 1" | bc) network latency usec vs size bits'; set yrange [0:*]; p '$tg' u 1:2, f(x) lc 'red'" #headroom test
  echo
done


t3=$(mktemp); echo "t3 $t3"; 
t4=$(mktemp); echo "t4 $t4"; 
t5=$(mktemp); echo "t5 $t5"; 

for i in {$hi_port..$base_port}; do grep $i $t|grep recd|tail -1; done; echo; grep sent $t|tail -1; echo -n "Attempting: "; grep Attempting $t|wc -l; echo -n "dropped: "; grep dropped $t|wc -l

t6=$(mktemp); echo "t6 $t6";
t7=$(mktemp); echo "t7 $t7";
grep "\[simulate_stream:] Sending frame" $t > $t6
grep "\[cpu_sim 700[123]\]:  frame size" $t >> $t6
grep done $t >> $t6
grep dropped $t >> $t6
sort -k 1 -n $t6 > $t7

t8=$(mktemp); echo "t8 $t8";
grep "simulate_stream:] Sending frame" $t7 > $t8
grep "cpu_sim $hi_port" $t7|grep done >> $t8
t9=$(mktemp); echo "t9 $t9";
sort -k 1 -n $t8 > $t9

t10=$(mktemp); echo "t10 $t10";
grep "cpu_sim $hi_port]:  Sending" $t7 > $t10
grep "cpu_sim 7002" $t7|grep done >> $t10
t11=$(mktemp); echo "t11 $t11";
sort -k 1 -n $t10 > $t11

t12=$(mktemp); echo "t12 $t12";
grep "cpu_sim 7002]:  Sending" $t7 > $t12
grep "cpu_sim $base_port" $t7|grep done >> $t12
t13=$(mktemp); echo "t13 $t13";
sort -k 1 -n $t12 > $t13

for i in $(seq "$base_port" "$hi_port"); do
    echo -n "Missed frame ratio for $i: "
    grep ratio $t|grep "cpu_sim $i"|cut -f 8 -d' '|tail -1
    grep ratio $t|grep "cpu_sim $i"|cut -f 1,8 -d' '|gnuplot -p -e "set title 'Missed Frames Component $(echo "$i - $base_port + 1" | bc)'; unset key; set xlabel 'Clock (hr)'; set ylabel 'Fraction'; p '-' u ((\$1/1e6)/3.6e3):2 w l"
done

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

# gnuplot -e "set xlabel 'X'; set ylabel 'Y'; plot '-' with lines" < data.txt
# "here document example"
#gnuplot -e "set xlabel 'X'; set ylabel 'Y'; plot '-' with lines" <<< $'1 2\n2 3\n3 5\n4 7\n5 11\ne'

# to rescale data:
# Example: Plot single-column data with scaling
# gnuplot -e "set xlabel 'Index'; set ylabel 'Scaled Value'; plot 'data.txt' using (\$0+1):(\$1/10.0) with lines title 'Scaled Data'"
# Explanation:
# \$0 + 1: Uses the line number as the x-axis (starts from 0, so we add 1).
# \$1 / 10.0: Scales the y-axis by dividing each value by 10.
# using (expr1):(expr2): Tells Gnuplot to evaluate expressions for x and y values.

