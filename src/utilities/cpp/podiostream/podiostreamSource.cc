

#include "podiostreamSource.h"

#include <JANA/JApplication.h>
#include <JANA/JEventSourceGeneratorT.h>
#include <JANA/JEvent.h>

#include <iostream>
#include <thread>
#include <string>
#include <chrono>

#include <TFile.h>
#include <TTree.h>
#include <TBufferFile.h>
#include <TKey.h>
#include <TError.h>

// These files are generated automatically by make_datamodel_glue.py
#include "services/io/podio/datamodel_glue.h"
#include "services/io/podio/datamodel_includes.h" // IWYU pragma: keep


// The following just makes this a JANA plugin
extern "C" {
    void InitPlugin(JApplication *app) {
        InitJANAPlugin(app);
        app->Add(new JEventSourceGeneratorT<podiostreamSource>());
    }
}



//------------------------------------------------------------------------------
// TODO: This copied from JEventSourcePODIO.cc 
//
// InsertingVisitor
//
/// This datamodel visitor will insert a PODIO collection into a JEvent.
/// This allows us to access the PODIO data through JEvent::Get and JEvent::GetCollection.
/// This makes it transparent to downstream factories whether the data was loaded from file, or calculated.
/// InsertingVisitor is called in GetEvent()
///
/// \param event             JANA JEvent to copy the data objects into
/// \param collection_name   name of the collection which will be used as the factory tag for these objects
//------------------------------------------------------------------------------
struct InsertingVisitor {
    JEvent& m_event;
    const std::string& m_collection_name;

    InsertingVisitor(JEvent& event, const std::string& collection_name) : m_event(event), m_collection_name(collection_name){};

    template <typename T>
    void operator() (const T& collection) {

        using ContentsT = decltype(collection[0]);
        m_event.InsertCollectionAlreadyInFrame<ContentsT>(&collection, m_collection_name);
    }
};

//------------------------------------------------------------------------------
// Constructor
//
///
/// \param resource_name  Name of root file to open (n.b. file is not opened until Open() is called)
/// \param app            JApplication
//------------------------------------------------------------------------------
podiostreamSource::podiostreamSource(std::string resource_name, JApplication* app) : JEventSource(resource_name, app)
    , context(1) , worker(context, ZMQ_PULL)   {
    SetTypeName(NAME_OF_THIS); // Provide JANA with class name

    LOG << "Creating podiostreamSource for \"" << resource_name << "\"" << LOG_END;

    app->SetDefaultParameter("podiostream:host", m_host, "Host name of podio TCP stream source to connect to an pull events from");
    app->SetDefaultParameter("podiostream:port", m_port, "Port number of podio TCP stream source to connect to an pull events from");

    // Setup network communication via zmq
    std::string url  = "tcp://" + m_host + ":" + m_port;
    worker.set(zmq::sockopt::rcvhwm, 1); // Set High Water Mark for maximum number of messages to queue before stalling
    worker.connect(url);
    LOG << "<--> PODIO stream will connect to: " << url << LOG_END;
}

//------------------------------------------------------------------------------
// Destructor
//------------------------------------------------------------------------------
podiostreamSource::~podiostreamSource() {
    LOG << "Closing Event Source for " << GetResourceName() << LOG_END;
}

//------------------------------------------------------------------------------
// Open
//------------------------------------------------------------------------------
void podiostreamSource::Open() {

    // LOG << "Opening podiostreamSource for \"" << GetResourceName() << "\"" << LOG_END;

    // // Setup network communication via zmq
    // worker.set(zmq::sockopt::rcvhwm, 10); // Set High Water Mark for maximum number of messages to queue before stalling
    // worker.connect("tcp://localhost:5557");
    // LOG << "Waiting for data ..." << LOG_END;
    // exit(-1);
}

//------------------------------------------------------------------------------
// GetEventBlock
//
/// Read another block of events from the network socket. 
//------------------------------------------------------------------------------
void podiostreamSource::GetEventBlock() {

    zmq::message_t task;
    auto res = worker.recv(task, zmq::recv_flags::none);

    // std::cout << "Received buffer: " << task.size() << std::endl;
    if( task.size() == 0 ){
        std::cout << "(skipping empty buffer)" << std::endl;
        throw RETURN_STATUS::kTRY_AGAIN;
    }

    // Delete existing TMemFile if it exists
    // TODO: This needs to be fixed for multi-threading operation
    if( m_memfile ) {
        delete m_memfile;
        m_memfile = nullptr;
        m_events_tree = nullptr;
        m_runs_tree = nullptr;
        m_metadata_tree = nullptr;
        m_podio_metadata_tree = nullptr;
    }
    if( m_myTM ){
        delete m_myTM;
        m_myTM = nullptr;
    }

    // The MyTMessage=TMessage=TBuffer object will assume ownership of the
    // buffer we pass into its constructor. Thus, we need to allocate a
    // new buffer that contains a copy so MyTMessage can own it.
    auto buff = new char[task.size()];
    memcpy( buff, (char*)task.data(), task.size() );

    // Create TMemFile from buffer
    m_myTM = new MyTMessage(buff, task.size()); // MyTMessage auto-deletes buff when it is destroyed
    // MyTMessage myTM((char*)task.data(), task.size());
    Long64_t length=0;
    m_myTM->ReadLong64(length);
    TDirectory *savedir = gDirectory;
    m_memfile = new TMemFile("tmpdir", m_myTM->Buffer() + m_myTM->Length(), length);
    savedir->cd();

    // Get pointers to TTrees
    if( m_memfile->IsOpen()){
        m_memfile->GetObject("events", m_events_tree);
        m_memfile->GetObject("runs", m_runs_tree);
        m_memfile->GetObject("metadata", m_metadata_tree);
        m_memfile->GetObject("podio_metadata", m_podio_metadata_tree);
    }

    // If data block, does not have an "events" tree in it,
    // assume that means we are done.
    if( m_events_tree == nullptr ) {
        // FIXME: Make sure m_memfile is deleted
        throw RETURN_STATUS::kNO_MORE_EVENTS;
    }

    // Tell podio RootReader to use the trees in the memfile
    m_reader.openTDirectory( m_memfile );

    // Initialze counters to process new block of events
    m_Nevents_processed_in_block = 0;
    m_Nevents_in_block = m_events_tree->GetEntries();
    m_Nevents_received += m_Nevents_in_block;

    LOG << "Received block of " << m_Nevents_in_block << " events." << LOG_END;

    // // Print ticker
    // auto Nbytes_received = task.size();
    // Long64_t Nevents_in_buffer = 0;
    // if( m_events_tree ) Nevents_in_buffer = m_events_tree->GetEntries();

    // auto now = std::chrono::high_resolution_clock::now();
    // auto duration = std::chrono::duration<double>(now - last_time).count();
    // auto rateMbps = Nbytes_received/duration*8.0/1.0E6;
    // auto rateHz = Nevents_in_buffer/duration;
    // auto savePrecision = std::cout.precision();
    // std::cout << "  " << std::fixed << std::setprecision(3) << rateHz << " Hz  (" << rateMbps << " Mbps)" << std::endl;
    // std::cout.precision(savePrecision);
    // last_time = now;
}

void podiostreamSource::GetEvent(std::shared_ptr <JEvent> event) {

    // LOG << "----------- GetEvent Called -----------------" << LOG_END;

    // Grab another event block from socket if needed.
    // This will throw exception if there is an issue
    if( m_Nevents_processed_in_block >= m_Nevents_in_block ) GetEventBlock();

    // return;
    
    auto frame_data = m_reader.readEntry("events", m_Nevents_processed_in_block);
    auto frame = std::make_unique<podio::Frame>(std::move(frame_data));

    const auto& event_headers = frame->get<edm4hep::EventHeaderCollection>("EventHeader"); // TODO: What is the collection name?
    if (event_headers.size() != 1) {
        throw JException("Bad event headers: Entry %d contains %d items, but 1 expected.", m_Nevents_processed_in_block, event_headers.size());
    }
    event->SetEventNumber(event_headers[0].getEventNumber());
    event->SetRunNumber(event_headers[0].getRunNumber());

    // Insert contents of frame into JFactories
    VisitPodioCollection<InsertingVisitor> visit;
    for (const std::string& coll_name : frame->getAvailableCollections()) {
        const podio::CollectionBase* collection = frame->get(coll_name);
        InsertingVisitor visitor(*event, coll_name);
        visit(visitor, *collection);
    }

    event->Insert(frame.release()); // Transfer ownership from unique_ptr to JFactoryT<podio::Frame>
    m_Nevents_processed_in_block++;
    m_Nevents_processed++;
}

std::string podiostreamSource::GetDescription() {

    /// GetDescription() helps JANA explain to the user what is going on
    return "";
}


template <>
double JEventSourceGeneratorT<podiostreamSource>::CheckOpenable(std::string resource_name) {

    /// CheckOpenable() decides how confident we are that this EventSource can handle this resource.
    ///    0.0        -> 'Cannot handle'
    ///    (0.0, 1.0] -> 'Can handle, with this confidence level'
    
    /// To determine confidence level, feel free to open up the file and check for magic bytes or metadata.
    /// Returning a confidence <- {0.0, 1.0} is perfectly OK!
    
    return (resource_name == "podiostreamSource") ? 1.0 : 0.0;
}
