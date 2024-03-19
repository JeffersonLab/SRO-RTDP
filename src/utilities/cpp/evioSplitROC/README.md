
This program relies on the EVIO library version 4.3.1. It is not integrated into the larger build scheme for rtdp at this time because it is only useful for a very specific pupose and requires a specific (old) verion of evio.

`evioSplitRoc`can be built with the 
following (run from within the evioSplitROC source directory):

~~~bash
git clone https://github.com/JeffersonLab/evio -b evio-4.3.1 evio-4.3.1.src
export evio_ROOT=${PWD}/evio-4.3.1
cmake -S evio-4.3.1.src -B evio_build -DCMAKE_INSTALL_PREFIX=${evio_ROOT}
cmake --build evio_build --target install  -j4 

cmake -S . -B build
cmake --build build
~~~

The executable will be left in the `build` directory.

Example: 
Run this on a partial evio file: 

~~~
> evioSplitRoc hd_rawdata_121090_050_100MB.evio 
Scanning hd_rawdata_121090_050_100MB.evio for ROC ids
Found 55 ROC ids in file
Will need to read through the file 4 more times
to write all ROC files.
Pass: 1 (18 rocs)
opening file: ROCfiles/roc001.evio
opening file: ROCfiles/roc011.evio
opening file: ROCfiles/roc012.evio
opening file: ROCfiles/roc013.evio
opening file: ROCfiles/roc014.evio
opening file: ROCfiles/roc015.evio
opening file: ROCfiles/roc016.evio
opening file: ROCfiles/roc017.evio
opening file: ROCfiles/roc018.evio
opening file: ROCfiles/roc019.evio
opening file: ROCfiles/roc020.evio
opening file: ROCfiles/roc021.evio
opening file: ROCfiles/roc022.evio
opening file: ROCfiles/roc025.evio
opening file: ROCfiles/roc026.evio
opening file: ROCfiles/roc027.evio
opening file: ROCfiles/roc028.evio
opening file: ROCfiles/roc031.evio
  100 events written    
Closing files
Wrote 127 events
37 rocs left ... 
Pass: 2 (18 rocs)
opening file: ROCfiles/roc032.evio
opening file: ROCfiles/roc033.evio
opening file: ROCfiles/roc034.evio
opening file: ROCfiles/roc035.evio
opening file: ROCfiles/roc036.evio
opening file: ROCfiles/roc037.evio
opening file: ROCfiles/roc038.evio
opening file: ROCfiles/roc039.evio
opening file: ROCfiles/roc040.evio
opening file: ROCfiles/roc041.evio
opening file: ROCfiles/roc042.evio
opening file: ROCfiles/roc051.evio
opening file: ROCfiles/roc052.evio
opening file: ROCfiles/roc053.evio
opening file: ROCfiles/roc054.evio
opening file: ROCfiles/roc055.evio
opening file: ROCfiles/roc056.evio
opening file: ROCfiles/roc057.evio
  100 events written    
Closing files
Wrote 127 events
19 rocs left ... 
Pass: 3 (18 rocs)
opening file: ROCfiles/roc058.evio
opening file: ROCfiles/roc059.evio
opening file: ROCfiles/roc060.evio
opening file: ROCfiles/roc061.evio
opening file: ROCfiles/roc062.evio
opening file: ROCfiles/roc063.evio
opening file: ROCfiles/roc064.evio
opening file: ROCfiles/roc071.evio
opening file: ROCfiles/roc073.evio
opening file: ROCfiles/roc075.evio
opening file: ROCfiles/roc077.evio
opening file: ROCfiles/roc078.evio
opening file: ROCfiles/roc082.evio
opening file: ROCfiles/roc083.evio
opening file: ROCfiles/roc084.evio
opening file: ROCfiles/roc090.evio
opening file: ROCfiles/roc092.evio
opening file: ROCfiles/roc094.evio
  100 events written    
Closing files
Wrote 127 events
1 rocs left ... 
Pass: 4 (1 rocs)
opening file: ROCfiles/roc095.evio
  100 events written    
Closing files
Wrote 127 events
0 rocs left ... 
~~~


