
/* srodump.c */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <time.h>

#include <evio/evio.h>

#define MAXCHAN 16

#include "sroPrint.h"

#define BUFFERSIZE 10000000
unsigned int buff[BUFFERSIZE];


int
main(int argc, char *argv[])
{
  int status, handler;
  char filename[256];

  if(argc!=2)
  {
    printf("\nUsage: sroprint <evio filename>\n\n");
    exit(0);
  }

  strcpy(filename,argv[1]);
  printf("\nUsing evio filename >%s<\n",filename);

  status = evOpen(filename, (char *)"r", &handler);
  printf("status=%d\n", status);
  if (status != 0)
  {
    printf("evOpen error %d - exit\n", status);
    exit(-1);
  }
  
  while(1)
  {
    status = evRead(handler, buff, BUFFERSIZE);
    if (status < 0)
    {
      if (status == EOF)
      {
	printf("evRead: end of file - exit\n");
        break;
      }
      else
      {
	printf("evRead error=%d - exit\n", status);
	break;
      }
    }
 
    sroPrintSet(&buff[0]);      
  }
  
  status = evClose(handler);

  exit(0);
}

