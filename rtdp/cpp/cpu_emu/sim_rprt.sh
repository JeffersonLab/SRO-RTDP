#!/bin/bash
#set -x
# ./sim_rprt <trace_file>
#==================================basic report =================================
set +m
grep "Estimated chunk rate" $1|cut -f6 -d' '|gnuplot -p -e "set title 'Sender Estimated chunk rate Hz'; p '-' w l" &
grep "Estimated bit rate" $1|grep MHz|cut -f6 -d' '|gnuplot -p -e "set title 'Sender Estimated bit rate Mhz'; p '-' w l" &
echo -n "Sender chunk size: "; grep -i "Sending chunk" $1|cut -f 6 -d' '|get_moments
echo -n "Sender Estimated chunk rate: "; grep -i "Estimated chunk rate" $1|cut -f6 -d' '|get_moments
echo -n "Sender Estimated bit rate: "; grep -i "Estimated bit rate" $1|cut -f6 -d' '|get_moments
echo -n "Sender Avg loop duration: "; grep -i "Avg loop duration" $1|cut -f6 -d' '|get_moments
grep "\[simulate_stream:\] zmq delay" $1|cut -f 6 -d' '|gnuplot -p -e "set title 'sender zmq delay usec'; p '-' w l" &
echo -n "sender zmq delay usec: "; grep "\[simulate_stream:\] zmq delay" $1|cut -f 6 -d' '|get_moments
grep "Avg loop duration" $1|cut -f 6 -d' '|gnuplot -p -e "set title 'Sender Avg loop duration (s)'; p '-' w l" &
echo -n "sender Rate slept: "; grep "Rate slept" $1|cut -f 6 -d ' '|get_moments
echo
t0=$(mktemp); echo "t0 $t0"
for i in {7001..7003}; do
    grep "cpu_emu ${i}" $1 > $t0
    echo "cpu_emu ${i}:"
    t1=$(mktemp); echo "t1 $t1"; grep "Measured chunk" $t0|grep -v "simulate_stream"|cut -f 8 -d' ' > $t1
    gnuplot -p -e "set title '$i Measured chunk Hz'; p '-' w l" < $t1 &
    echo -n "Measured chunk: "; get_moments < $t1
    t2=$(mktemp); echo "t2 $t2"; grep "Measured bit" $t0|grep -v "simulate_stream"|cut -f 8 -d' '  > $t2
    gnuplot -p -e "set title '$i Measured bit Hz'; p '-' w l"  < $t2 &
    echo -n "Measured bit: "; get_moments  < $t2
    echo -n "Sim Sleep Spec: "; grep -i "Sim Sleep Spec" $t0|grep -v "simulate_stream"|cut -f 8 -d' '|get_moments
    echo -n "Transmission Sleep Spec: "; grep -i "Transmission Sleep Spec" $t0|grep -v "simulate_stream"|cut -f 8 -d' '|get_moments
    echo -n "chunk size: "; grep -i "Chunk size" $t0|grep -v "simulate_stream"|cut -f 9 -d' '|get_moments
    t3=$(mktemp); echo "t3 $t3"; grep ZMQ $t0|grep -v "simulate_stream"|cut -f 8 -d' '|tail -n +2   > $t3
    echo -n "ZMQ read duration (us): "; get_moments < $t3
    gnuplot -p -e "set title '$i ZMQ read duration (us)'; p '-' w l" < $t3 &
  echo
done


grep "\[cpu_emu 7003 \]:  Measured chunk rate" $t|cut -f 1,13 -d' '>/tmp/junk0; grep -i "Estimated chunk rate" $t|cut -f 1,9 -d' '>/tmp/junk1; paste /tmp/junk[01]|tee /tmp/junk3|gnuplot -p -e "set title 'sent/recd'; p '-' u 1:2 w l lc 'red', '/tmp/junk3' u 3:4 w l lc 'green'" &

grep recd $t|tail -1; grep sent $t|tail -1; grep Attempting $t|wc -l; grep dropped $t|wc -l


set -m
