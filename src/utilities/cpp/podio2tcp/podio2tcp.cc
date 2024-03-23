
#include <iostream>
#include <thread>
#include <chrono>

#include <TFile.h>
#include <TMemFile.h>
#include <TTree.h>
#include <TMessage.h>
#include <TBufferFile.h>

#include <zmq.hpp>

int main(int narg, char *argv[]){

    // Setup zmq communication
    zmq::context_t context(1);
    zmq::socket_t ventilator(context, ZMQ_PUSH);
    ventilator.bind("tcp://*:5557");

    // Open input file
    auto fname = "eic/simout.100.edm4hep.root";
    auto f = new TFile(fname);
    if( ! f->IsOpen() ){
        std::cerr << "Unable to open file: " << fname << std::endl;
        return -1;
    }

    // Get pointer to events tree
    TTree* events_tree = nullptr;
    f->GetObject("events", events_tree);
    auto Nevents = events_tree->GetEntries();
    std::cout << "events tree: " << Nevents << " entries" << std::endl;

    // Get pointers to the metadata trees
     TTree* runs_tree = nullptr;
     TTree* metadata_tree = nullptr;
     TTree* podio_metadata_tree = nullptr;
     f->GetObject("runs", runs_tree);
     f->GetObject("metadata", metadata_tree);
     f->GetObject("podio_metadata", podio_metadata_tree);

    // Optionally Loop over file continuously
    Long64_t Nevents_per_group = 50;
    Long64_t Nevents_sent_total = 0;
    bool loop = true;
    static auto last_time = std::chrono::high_resolution_clock::now();
    double rateHz = 0.0;
    do{
        // Loop over all events in file, sending in groups of events
        Long64_t Nevents_sent = 0;
        while( Nevents_sent < Nevents ){

            auto savedir = gDirectory;  // save current root directory

            // Create a memory resident file for the copied tree(s) to be placed in
            TMemFile memfile("tmpdir", "recreate");

            // Copy trees into memory resident file.
            // The events tree only copies a range of events while the metadata trees
            // (which have single entires) are copied completely.
            // These copies are automatically destroyed when memfile goes out of scope.
            auto t = events_tree->CopyTree("", "", Nevents_per_group, Nevents_sent);  // CopyTree(selection, options, nentries, firstentry)
            if( runs_tree           ) runs_tree->CopyTree("");
            if( metadata_tree       ) metadata_tree->CopyTree("");
            if( podio_metadata_tree ) podio_metadata_tree->CopyTree("");

            savedir->cd();  // restore root directory

            // Create a TMessage object and serialize the TMemfile into it
            TMessage *tm = new TMessage(kMESS_ANY);
            memfile.Write();
            tm->WriteLong64(memfile.GetEND()); // see treeClient.C ROOT tutorial
            memfile.CopyTo(*tm);
            std::cout << "memfile.GetEND()=" << memfile.GetEND() << std::endl;

            // Print ticker
            std::cout << "Buffer size: " << tm->Length() << "  events: " << t->GetEntries() << "  start event: " << Nevents_sent << "  (total sent: " << Nevents_sent_total << " -- " << rateHz << " Hz)" << std::endl;

            // Send the message (will block if no receiver attached until one is)
            zmq::message_t message(tm->Buffer(), tm->Length());
            bool sent = ventilator.send(message, zmq::send_flags::dontwait).has_value();
            if( sent ){
                Nevents_sent += t->GetEntries();
                Nevents_sent_total += t->GetEntries();
            }else{
                std::cout << "Unable to send (is receiver running?). Waiting 3 seconds..." << std::endl;
                std::this_thread::sleep_for(std::chrono::seconds(3));
            }

            // Calculate event rate for this iteration
            auto now = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration<double>(now - last_time).count();
            rateHz = t->GetEntries()/duration;
            last_time = now;
        }
    
    }while( loop ); // If looping continuously over events in file

    return 0;
}