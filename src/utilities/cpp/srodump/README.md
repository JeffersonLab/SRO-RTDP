

Here is a simple program for parsing a SRO EVIO file that Sergey Boiarinov wrote. It has been tweaked and a CMakeLists.txt file added to build it against the evio 4.3.1 library. Detailed instructions are:


Build EVIO (or just set evio_ROOT to point to existing installation)
~~~bash
git clone -b evio-4.3.1 https://github.com/JeffersonLab/evio evio-4.3.1.src
cmake -S evio-4.3.1.src -B evio-4.3.1.build -DCMAKE_INSTALL_PREFIX=${PWD}/evio-4.3.1
cmake --build evio-4.3.1.build --target install -j 8
export evio_ROOT=${PWD}/evio-4.3.1
~~~

Build the `srodump` executable
~~~bash
cmake -S srodump -B srodump.build -DCMAKE_INSTALL_PREFIX=${PWD}/srodump.install
cmake --build srodump.build
~~~

Run the `srodump` executable
~~~bash
./srodump.build/srodump sro_000494.evio.00000
~~~

