#!/bin/bash

OP_HOSTFILE=<HOST FILE>
OP_N=<NODE COUNT>
OP_TO=<COMMA SEPARATED TO EMAIL LIST>
OP_FROM=<FROM EMAIL - MUST BE A GMAIL ACCOUNT>
OP_FROMPW=<FROM EMAIL PASSWORD>

OP_CUT=1.5
OP_DATAPOINTS=200000
OP_T=1
OP_P=24
OP_THREAD=0
OP_ITERATIONS=100
OP_BLOCKSIZE=64
OP_PRINTFREQ=50
OP_STOPFILE=stop

wd=`pwd`
x='x'
pat=$OP_T$x$OP_P$x$OP_N
OP_OUTDIR=$wd/$pat

run=1
while true; do
    if [ -f $OP_STOPFILE ]
    then
	printf "Stopping runs as file: %s found\n" $OP_STOPFILE
	break;
    fi
    if [ -d $OP_OUTDIR ]
    then
        rm -rf $OP_OUTDIR
        mkdir -p $OP_OUTDIR
    fi

    hostfile=$OP_HOSTFILE
    for line in `cat $hostfile`;do
        for (( c=0; c<$OP_P; c++ ))
        do
        	OP_RANK=$c
        	BIND_TO=$OP_RANK,$(($OP_RANK+$OP_P))
        	ssh $line $wd/run.generic.sh $OP_DATAPOINTS $OP_T $OP_P $OP_N $OP_RANK $OP_THREAD $OP_ITERATIONS $OP_BLOCKSIZE $OP_PRINTFREQ $OP_OUTDIR $BIND_TO $pat &
    	sleep 0.2
        done
    done

    sleep 3

    while true; do
	flag=0
	for line in `cat $hostfile`; do
	    count=`ssh $line pgrep java | wc -l`
	    if [ $count -ne 0 ]
	    then
		flag=1
		break
	    fi
	done

	if [ $flag -ne 0 ]
	then
	    sleep 5
	else
	    break
	fi
    done

    $wd/run.analyzer.sh $OP_OUTDIR $OP_CUT $OP_TO $OP_FROM $OP_FROMPW
    printf "Run %s completed on %s\n" $run `date` >> log.run.txt
    run=$(($run+1))
done
