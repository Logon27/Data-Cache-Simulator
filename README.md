#  Data Cache Simulator

This program simulates the behavior of a data cache. The program reads a trace of references from standard input and produces statistics about the trace to standar output.

## Example Trace.config file

Number of sets: 8  
Set size: 1  
Line size: 8  

## Trace of References

The trace of references are read from stdin and have the following format:  
`<accesstype>:<size>:<hexaddress>`

<accesstype> can be the characters R (which indicates read access) or W (which indicates write access).
<size> is the size of the reference in bytes.
<hexaddress> is the starting byte address of the reference expressed as a hexadecimal number

## Example trace.dat input file

R:4:b0  
R:4:d0  
R:4:b0  
R:4:d0  
R:4:80  
R:4:18  
R:4:80  
R:4:90  
R:4:80  

## Example trace.dat output file
<img src="https://i.imgur.com/9CugtQp.png">
