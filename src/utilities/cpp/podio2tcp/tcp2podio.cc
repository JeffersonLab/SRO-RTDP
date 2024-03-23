//
// This is an example program that can establish a tcp connection to a
// podio2tcp instance and receive events from it. It deomstrates how
// to unpack the packets of events and access the podio trees they
// contain. Each packet contains the full metadata trees and an
// events tree with one or more events in it.
//
//

#include <iostream>
#include <thread>
#include <string>

#include <TFile.h>
#include <TMemFile.h>
#include <TTree.h>
#include <TMessage.h>
#include <TBufferFile.h>
#include <TKey.h>
#include <TError.h>

#include <zmq.hpp>

// special class needed to expose protected TMessage constructor
class MyTMessage : public TMessage {
public:
   MyTMessage(void *buf, Int_t len) : TMessage(buf, len) { }
};


int main(int narg, char *argv[]){

    // This suppresses those annoying warnings about no dictionary when
    // the ROOT file is opened. (It probably suppresses others too.)
    gErrorIgnoreLevel = kError;

    // Setup network communication via zmq
    zmq::context_t context(1);
    zmq::socket_t worker(context, ZMQ_PULL);
    worker.set(zmq::sockopt::rcvhwm, 10); // Set High Water Mark for maximum number of messages to queue before stalling
    worker.connect("tcp://localhost:5557");

    std::cout << "Waiting for data ..." << std::endl;
    while (true) {
            zmq::message_t task;
            auto res = worker.recv(task, zmq::recv_flags::none);

            std::cout << "Received buffer: " << task.size() << std::endl;
            if( task.size() == 0 ){ std::cout << "(skipping empty buffer)" << std::endl; continue;}

            // Create TMemFile from buffer
            MyTMessage *myTM = new MyTMessage((char*)task.data(), task.size());
            Long64_t length=0;
            myTM->ReadLong64(length);
            TDirectory *savedir = gDirectory;
            TMemFile *f = new TMemFile("tmpdir", myTM->Buffer() + myTM->Length(), length);
            savedir->cd();

            // Get pointers to TTrees
            TTree* events_tree = nullptr;
            TTree* runs_tree = nullptr;
            TTree* metadata_tree = nullptr;
            TTree* podio_metadata_tree = nullptr;
            if( f->IsOpen()){
                f->GetObject("events", events_tree);
                f->GetObject("runs", runs_tree);
                f->GetObject("metadata", metadata_tree);
                f->GetObject("podio_metadata", podio_metadata_tree);
            }

            // auto Nevents = events_tree->GetEntries();
            std::cout << "events: " << (events_tree!=nullptr ? events_tree->GetEntries():-1)
                      << " runs: " << (runs_tree!=nullptr ? runs_tree->GetEntries():-1) 
                      << " metadata: " << (metadata_tree!=nullptr ? metadata_tree->GetEntries():-1) 
                      << " podio_metadata: " << (podio_metadata_tree!=nullptr ? podio_metadata_tree->GetEntries():-1) 
                      << std::endl;

            delete f;

            // std::this_thread::sleep_for(std::chrono::seconds(1));
    }

    return 0;
}