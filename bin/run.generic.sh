#!/bin/bash
cp=$HOME/.m2/repository/com/google/guava/guava/15.0/guava-15.0.jar:$HOME/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar:$HOME/.m2/repository/javax/activation/activation/1.1.1/activation-1.1.1.jar:$HOME/.m2/repository/javax/mail/mail/1.5.0-b01/mail-1.5.0-b01.jar:$HOME/.m2/repository/edu/indiana/soic/spidal/mmbench/1.0-SNAPSHOT/mmbench-1.0-SNAPSHOT.jar

memmul=1
opts="-XX:+UseG1GC -Xms256m -Xmx$(($2*$memmul))g"

hostname=`hostname`
pat=${12}

wd=${10}
outdir=$wd/$hostname
distributiondir=$outdir/distribution
mkdir -p $distributiondir

taskset -c ${11} java $opts -cp $cp edu.indiana.soic.spidal.serial.Program -dataPoints $1 -tpp $2 -ppn $3 -n $4 -rank $5 -thread $6 -iterations $7 -blockSize $8 -printFreq $9 -outputDir $distributiondir 2>&1 | tee $outdir/$hostname.$pat.$1.r$5.t$6.txt

