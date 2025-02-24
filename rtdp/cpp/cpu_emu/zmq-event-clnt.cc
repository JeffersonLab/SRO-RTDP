//
//  Basic ZeoMQ client in C++
//  Connects REQ socket to tcp://localhost:5555
//  Sends 10MB "event" to server, expects back
//
#include <zmq.hpp>
#include <string>
#include <iostream>

int main ()
{
    //  Prepare our context and socket
    zmq::context_t context (1);
    zmq::socket_t socket (context, zmq::socket_type::req);

    std::cout << "Connecting to server..." << std::endl;
    socket.connect ("tcp://ejfat-fs-daq:5555");

    //  Do 10 requests, waiting each time for a response
    for (int request_nbr = 0; request_nbr != 2; request_nbr++) {
	// Send 10MB "event"
        zmq::message_t request (10*1024*1024);
        std::cout << "Sending  " << request_nbr << "..." << std::endl;
        socket.send (request, zmq::send_flags::none);

        //  Get the reply.
        zmq::message_t reply;
        zmq::recv_result_t rtcd = socket.recv (reply, zmq::recv_flags::none);
        //std::cout << "Connect return code: " << rtcd << '\n';
        std::cout << "Received  " << request_nbr << "rtcd = " << rtcd.value() << " Actual reply: " << reply << std::endl;
    }
    return 0;
}
