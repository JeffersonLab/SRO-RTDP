
This program relies on the EVIO library version 4.3.1. It can be built with the 
following:

~~~bash
git clone https://github.com/JeffersonLab/evio -b evio-4.3.1 evio-4.3.1.src
export evio_ROOT=${PWD}/evio-4.3.1
cmake -S evio-4.3.1.src -B evio_build -DCMAKE_INSTALL_PREFIX=${evio_ROOT}
cmake --build evio_build --target install  -j4 

cmake -S . -B build
cmake --build build
~~~


