

#pragma once

#include <JANA/JApplication.h>
#include <JANA/JEvent.h>
#include <JANA/JEventSource.h>
#include <JANA/JEventSourceGeneratorT.h>
#include <podio/ROOTReader.h>
#include <stddef.h>
#include <memory>
#include <set>
#include <string>

#include <TMemFile.h>
#include <TMessage.h>


#include <zmq.hpp>


class podiostreamSource : public JEventSource {

    /// Add member variables here

public:
    podiostreamSource(std::string resource_name, JApplication* app);

    virtual ~podiostreamSource();

    void Open() override;

    void GetEvent(std::shared_ptr<JEvent>) override;
    
    static std::string GetDescription();

protected:

    // special class needed to expose protected TMessage constructor
    class MyTMessage : public TMessage {
    public:
        MyTMessage(void *buf, Int_t len) : TMessage(buf, len) { }
    };

    void GetEventBlock();


    podio::ROOTReader m_reader;
    size_t m_Nevents_in_block  = 0;
    size_t m_Nevents_received  = 0;
    size_t m_Nevents_processed = 0;
    size_t m_Nevents_processed_in_block = 0;

    std::string m_include_collections_str;
    std::string m_exclude_collections_str;
    std::set<std::string> m_INPUT_INCLUDE_COLLECTIONS;
    std::set<std::string> m_INPUT_EXCLUDE_COLLECTIONS;
    bool m_run_forever=false;

    zmq::context_t context;
    zmq::socket_t worker;
    // Long64_t m_event_in_tree = 0;

    MyTMessage *m_myTM = nullptr;
    TMemFile *m_memfile = nullptr;
    TTree* m_events_tree = nullptr;
    TTree* m_runs_tree = nullptr;
    TTree* m_metadata_tree = nullptr;
    TTree* m_podio_metadata_tree = nullptr;
    
};

template <>
double JEventSourceGeneratorT<podiostreamSource>::CheckOpenable(std::string);


