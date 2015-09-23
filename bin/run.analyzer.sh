#!/bin/bash
cp=$HOME/.m2/repository/com/google/guava/guava/15.0/guava-15.0.jar:$HOME/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar:$HOME/.m2/repository/javax/activation/activation/1.1.1/activation-1.1.1.jar:$HOME/.m2/repository/javax/mail/mail/1.5.0-b01/mail-1.5.0-b01.jar:$HOME/.m2/repository/edu/indiana/soic/spidal/mmbench/1.0-SNAPSHOT/mmbench-1.0-SNAPSHOT.jar

java -cp $cp edu.indiana.soic.spidal.tools.AnalyzeResults -outputDir $1 -cut $2 -to $3 -from $4 -frompw $5 2>&1 | tee analyzer.status.txt
