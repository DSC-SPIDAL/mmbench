mmbench
=====

Block matrix multiply benchmark to identify if a node is performing slower than others in a cluster

Prerequisites
-----
1. Operating System
  * This program should generally work on any Linux based operating system. It has been extensively tested and known to work on,
    *  Red Hat Enterprise Linux Server release 6.7 (Santiago)
  
2. Java
  * Download Oracle JDK 8 from http://www.oracle.com/technetwork/java/javase/downloads/index.html
  * Extract the archive to a folder named `jdk1.8.0`
  * Set the following environment variables.
  ```
    JAVA_HOME=<path-to-jdk1.8.0-directory>
    PATH=$JAVA_HOME/bin:$PATH
    export JAVA_HOME PATH
  ```
3. Apache Maven
  * Download latest Maven release from http://maven.apache.org/download.cgi
  * Extract it to some folder and set the following environment variables.
  ```
    MVN_HOME=<path-to-Maven-folder>
    $PATH=$MVN_HOME/bin:$PATH
    export MVN_HOME PATH
  ```

Building mmbench
-----
* Check all prerequisites are satisfied before building mmbench
* Clone this git repository from `git@github.com:DSC-SPIDAL/mmbench.git` Let's call this directory `mmbenchhome`
* Once above two steps are completed, building mmbench requires only one command, `mvn install`, issued within `mmbenchhome`.

Running mmbench
-----
Change the following parameters in `$mmbenchhom/bin/run.sh`
```sh
OP_HOSTFILE=<List each host IP or name in a new a line>
OP_N=<Number of nodes to mimic. This could be a number greater than or equal to the total hosts in the $OP_HOSTFILE>
OP_P=<Number of processes per node, which should be less than or equal to the number of cores per socket divided by OP_T, which is number of threads per process>
OP_TO=<Comma separated email addresses to send any slow behavior of nodes>
OP_FROM=<From email address - must be a gmail address>
OP_FROMPW=<Password for the from email address>
```

Then remove the designated stop file if exists. This is by default named `stop`, so if found please remove it 
using the following `rm` command.

```
rm stop
```

Now invoke the `$mmbenchhome/bin/run.sh`

Stopping mmbench
-----
The `$mmbenchhome/bin/run.sh` will continously run the benchmark unless it finds the designated stop file,
which is by default is named as `stop`. Therefore, to stop mmbench simply do the following within the working
directory of run script.

```
touch stop
```

Behavior of mmbench
-----
The mmbench program performs block matrix multiplication and is designed to test any given data decomposition
making it easy to mimic a real run that would otherwise need a large number of nodes. For example one could specify
the number of threads per process (`OP_T`), number of processes per node (`OP_P`), and the number of nodes (`OP_N`) 
that compose the real run. The benchmark will then create `OP_T x OP_P` processes per node on each host specified
in the hostfile (`OP_HOSTFILE`). Each process will multiply two matrices of sizes `PxQ` and `QxR` where 
`P=(OP_DATAPOINTS/(OP_T x OP_P))`, `Q=OP_DATAPOINTS`, and `R=3`. Note. P is actually the integer division of values
in its right hand side. If there's a remainder then some processes will handle P+1 rows to based on the load balancing 
technique used. 

The benchmark will be run for `OP_ITERATIONS` and the results will be automatically analyzed at the end. If the total time
a node has taken is greater or equal to `OP_CUT * MIN` then the list of such nodes will be emailed to the designated addresses.
The `MIN` is the minimum time a node has taken to complete all iterations.

The run script will run the benchmark continously until signaled to stop. If an anomaly is found the result for that run
will be backed up automatically into a directory with timestamp of the run and other meta information. If all nodes performed
uniformly then next run of will overwrite the data to save disk space.

