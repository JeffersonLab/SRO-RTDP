//  Splits raw data file into one file per ROC

//  Data is stored in big endian, which is needed on the ROC since VME delivers
//   data in big endian

//  Currently only does blocklevel=1

//  ejw, 5-Dec-2013

// g++ -std=c++11 -o evioSplitRoc evioSplitRoc.cc -I/group/da/ejw/coda/include -L/group/da/ejw/coda/Linux-x86_64/lib -levioxx -levio -lexpat -lrt


#include <memory>
#include <string>
#include <sstream>
#include <iomanip>
#include <set>
#include <vector>

#include <sys/stat.h>

#include "evioBankIndex.hxx"
#include "evioUtil.hxx"
#include "evioFileChannel.hxx"

extern "C" {
#include "evio.h"
#ifndef swap_int32_t
//uint32_t* swap_int32_t(uint32_t*,int,uint32_t*);
#endif
}


using namespace evio;


static string dname = "ROCfiles";
static int maxRoc = 18; // make this 18 if using EVIO <4.1 !
static int maxEvt = 0;


#define _DBG_ cerr<<__FILE__<<":"<<__LINE__<<" "
#define _DBG__ cerr<<__FILE__<<":"<<__LINE__<<endl;


void GetROCNums(string fname, int maxEvt, set<int> &rocnums);
void ScanFile(string fname, int maxEvt, vector<int> &rocnums);


//-------------------------------------------------------------------------------

//------------------
// main
//------------------
int main(int argc, char **argv) {
  
  	// Make sure user gives a filename
	if(argc<2){
		cout << endl << "Usage:\n   evioSplit input-file [maxEvt]" << endl << endl;
		exit(EXIT_FAILURE);
	}
	
	string fname(argv[1]);
	
	// get max events
	if(argc>2)  maxEvt=atoi(argv[2]);

	// Get the list of ROC ids by scanning the file
	set<int> rocnums;
	GetROCNums(fname, maxEvt, rocnums);

	// EVIO <4.1 or so has a hardwired limit of having 20 files
	// open at once (including the one we're reading from).
	// Calculate number of times we'll need to scan through the
	// input file in order to write out all of the ROC ids given this
	// limitation.
	int Npasses = 1 + (rocnums.size()-1)/maxRoc;
	
	if(rocnums.empty()){
		cout << "Nothing to do!" << endl;
		exit(EXIT_SUCCESS);
	}
	
	cout << "Will need to read through the file " << Npasses << " more times" << endl;
	cout << "to write all ROC files." << endl;

	// Make folder to hold output files
	mkdir(dname.c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH);
	
	int ipass = 0;
	while(rocnums.size() > 0){
		
		// Copy first maxRoc ids into separate container
		vector<int> rocnums_subset;
		while(rocnums_subset.size()<(unsigned int)maxRoc && rocnums.size()>0){
			int roc = *(rocnums.begin());
			rocnums_subset.push_back(roc);
			rocnums.erase(roc);
		}

		cout << "Pass: " << ++ipass << " (" << rocnums_subset.size() << " rocs)" << endl;
		
		ScanFile(fname, maxEvt, rocnums_subset);
		
		cout << rocnums.size() << " rocs left ... " << endl;
	}

	// done
	exit(EXIT_SUCCESS);
}

//------------------
// GetROCNums
//------------------
void GetROCNums(string fname, int maxEvt, set<int> &rocnums)
{
	// Get the complete list of ROC ids from the file
	// by reading over all events once.
  try {

    // open input file channel
    evioFileChannel *chan = new evioFileChannel(fname.c_str(),"r");
    chan->open();
	 
	 cout << "Scanning " << fname << " for ROC ids" << endl;

    // loop over all buffers in file, copy data, swap, then write
    int nev=0;
	 int nev_since_changed = 0;
    while(chan->readNoCopy()) {
      nev++;
		nev_since_changed++;

      // max events
      if((maxEvt>0)&&(nev>maxEvt))break;

      // get bank index at level 1
      evioBankIndex index(chan->getNoCopyBuffer(),1);

      
      // loop over all banks
		bankIndexMap::iterator tnMap = index.tagNumMap.begin();
      for(; tnMap!=index.tagNumMap.end(); tnMap++) {
		
			// Check that this is a bank of banks (excludes EPICS events)
			if( tnMap->second.containerType != 0x10 ) continue;

        // check tag
		  // 8/17/2018 DL - The code below was revised for CODA 3.0.8
		  // where the EVIO library changed to use a different format
		  // for evioDictionary. It now uses a evioDictEntry object which
		  // stores the Tag and Num such that they must be accessed through
		  // accessor methods. The fix below was not checked, only made to
		  // compile so the rest of the packages could be built.
        int tag = tnMap->first.first;
		//   int tag = (tnMap->first.getTag()<<16) + (tnMap->first.getNum());
		  
		  // ignore built trigger banks that have tag=0xFF2X
		  if( (tag & 0xFF200000) == 0xFF200000 ) continue;
		  
		  // Some events seem to have large tag values in
		  // events not containing valid ROC data. Ignore
		  // these since they cause the copier below to choke
		  // since their "nwords" values are bogus
		  if(tag > 1000) continue;
		  
		  if(rocnums.find(tag) == rocnums.end()){
		    rocnums.insert(tag);
			 nev_since_changed = 0;
		  }
		  
      }
		
		// If we have already found some rocsnums but have not seen any
		// new ones for the last 1000 events then assume we've found them all
		if( (!rocnums.empty()) && (nev_since_changed>1000) ) break;
    }
	 
	 chan->close();
	 
	 cout << "Found " << rocnums.size() << " ROC ids in file" << endl;

  } catch (evioException e) {
    cerr << e.what() << endl;
    exit(EXIT_FAILURE);
  }
}

//------------------
// ScanFile
//------------------
void ScanFile(string fname, int maxEvt, vector<int> &rocnums)
{
  
	uint32_t buffer[100000];

	try {

		// open input file channel
		evioFileChannel *chan = new evioFileChannel(fname.c_str(),"r");
		chan->open();


		// open one output file per ROC
		map<int, evioFileChannel*> ovec;
		for(unsigned int i=0; i<rocnums.size(); i++) {
			stringstream sname;
			int roc = rocnums[i];
			sname << dname << "/roc" << setfill('0') << setw(3) << roc << ".evio" << ends;
			cout << "opening file: " << sname.str() << endl;
			ovec[roc]=new evioFileChannel(sname.str(),"w");
			ovec[roc]->open();
		}

		// loop over all buffers in file, copy data, swap, then write
		int nev=0;
		while(chan->readNoCopy()) {
			nev++;

			// get bank index at level 1
			evioBankIndex index(chan->getNoCopyBuffer(),1);

			// loop over all banks
			bankIndexMap::iterator tnMap = index.tagNumMap.begin();
			for(; tnMap!=index.tagNumMap.end(); tnMap++) {

				// Check that this is a bank of banks (excludes EPICS events)
				if( tnMap->second.containerType != 0x10 ) continue;

				// get tag (=rocid)
			  // 8/17/2018 DL - The code below was revised for CODA 3.0.8
			  // where the EVIO library changed to use a different format
			  // for evioDictionary. It now uses a evioDictEntry object which
			  // stores the Tag and Num such that they must be accessed through
			  // accessor methods. The fix below was not checked, only made to
			  // compile so the rest of the packages could be built.
      	  int tag = tnMap->first.first;
			//   int tag = (tnMap->first.getTag()<<16) + (tnMap->first.getNum());
				
		  		// ignore built trigger banks that have tag=0xFF2X
		  		if( (tag & 0xFF200000) == 0xFF200000 ) continue;

				// Skip rocs we're not writing out
				if(ovec.find(tag) == ovec.end())continue;
				
				// get main ROC bank
				const uint32_t *bank = tnMap->second.bankPointer;

				// Copy all banks that have at least one data
				// word. This will include the DAQ config bank,
				// but will exclude what Elliot called the "trigger
				// bank'' which seemed to be an empty bank of uint32_t
				// In order to write more than one bank of uint32_t
				// we need to wrap it in a bank-of-banks
				uint32_t *buff = buffer;
				buff[0] = 1; // initialize total number of words with top-level header
				buff[1] = (tag<<16) + (0x10<<8) + (1<<0); //0x10=bank of banks
				buff = &buff[2];
				
				const uint32_t *iptr = &bank[2]; // length word of first uint32_t
				const uint32_t *iend = &bank[bank[0] + 1]; // first word after end of all banks
				while(iptr < iend){
					int nwords = *iptr + 1;

					if(nwords > 2){
						// Only keep banks with some data
						size_t size = nwords*sizeof(uint32_t);
						memcpy((void*)buff, (void*)iptr, size);
						
						// convert data words to big endian, but NOT header words
						swap_int32_t(&(buff[2]), nwords-2, NULL);
						
						buff =&buff[nwords];
						buffer[0] += nwords;
					}
					iptr = &iptr[nwords];
				}
				ovec[tag]->write(buffer);


#if 0
				// skip trigger bank, copy data bank to local buffer
				int nwords = 3+bank[2];
				size_t size = (bank[nwords]+1)*sizeof(uint32_t);
				memcpy((void*)buffer, (void*)&bank[nwords], size);

				// convert data words to big endian, but NOT header words
				swap_int32_t(&(buffer[2]), buffer[0]-1, NULL);

				// write converted data bank
				ovec[tag]->write(buffer);
#endif

			}

			if(nev%100 == 0){
				cout << "  " << nev << " events written    \r";  cout.flush();
			}

			// max events
			if((maxEvt>0)&&(nev>=maxEvt))break;
		}


		// close all files
		cout << endl;
		cout << "Closing files" << endl;
		for(unsigned int i=0; i<rocnums.size(); i++) {
			ovec[rocnums[i]]->close();
		}
		
		chan->close();

		cout << "Wrote " << nev << " events" << endl;

	} catch (evioException e) {
		cerr << e.what() << endl;
		exit(EXIT_FAILURE);
	}
}

//-------------------------------------------------------------------------------
