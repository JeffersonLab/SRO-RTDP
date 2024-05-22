// This was copied from:
// /usr/clas12/release/1.4.0_streaming/coda/src/dac_may_7_2024/main/obsolete/coda_sro.c_65
static int payload2slot[17] = 
{
/*0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16 - payloads*/
  0, 10, 13,  9, 14,  8, 15,  7, 16,  6, 17,  5, 18,  4, 19,  3, 20 /*slots*/
};

// The following sent by Sergey B. in e-mail on May 15, 2024.
// Other source was pointed to in directory /usr/clas12/release/1.4.0_streaming/
int sroPrintSet(int *buf)
{
  int ii, jj, kk, payload, slot, val, q, ch, t, pattern_28_00, pattern_47_29, nslots, padding, module_id, slot_len, totlen, len, len1, len2, len3, len4, len5;
  int slots[MAXCHAN]; 
  unsigned short *b16;

  totlen=buf[0]+1;
  printf("\n\ntotlen=%d\n",totlen);
  //for(ii=0; ii<totlen; ii++) printf("=============== [%2d] 0x%08x\n",ii,buf[ii]);printf("\n");
  ii = 0;

  printf("\n=== AGGREGATOR BANK ===\n\n");
  printf("[%5d] AGG length = %d words\n",ii,buf[ii]); ii++;
  printf("[%5d] AGG 2nd word: 0x%08x\n",ii,buf[ii]); ii++;
  printf("[%5d] SIB length = %d words\n",ii,buf[ii]); ii++;
  printf("[%5d] SIB 2nd word: 0x%08x\n",ii,buf[ii]); ii++;
  len=buf[ii]&0xFFFF;
  printf("[%5d] TSS: 1st word=0x%08x (len=%d), frame#=%d, timestamp_l=0x%08x, timestamp_h=0x%08x\n",
 ii,buf[ii],len,buf[ii+1],buf[ii+2],buf[ii+3]);
  ii+=4;
  len1=buf[ii]&0xFFFF;
  printf("[%5d] AIS: 1st word=0x%08x (len1=%d)\n",ii,buf[ii],len1); ii++;
  for(jj=0; jj<len1; jj++)
  {
    printf("[%5d] ROC[%2d]: 0x%08x\n",ii,jj,buf[ii]);
    ii++;
  }
  
  printf("\n=== DATA FROM ROCS ===\n\n");
  while(ii<totlen)
  {
    len2 = buf[ii];
    printf("\n[%5d] Time slice bank length len2=%d words\n",ii,len2); ii++;
    printf("[%5d] Time slice bank 2nd word = 0x%08x (ROCID=%d)\n",ii,buf[ii],buf[ii]>>16); ii++;
    len3 = buf[ii];
    printf("[%5d] SIB length len3=%d words\n",ii,len3); ii++;
    printf("[%5d] SIB 2nd word: 0x%08x\n",ii,buf[ii]); ii++;
    len4=buf[ii]&0xFFFF;
    printf("[%5d] TSS: 1st word=0x%08x (len4=%d), frame#=%d, timestamp_l=0x%08x, timestamp_h=0x%08x\n",ii,buf[ii],len4,buf[ii+1],buf[ii+2],buf[ii+3]);
    ii+=4;
    len5=buf[ii]&0xFFFF;
    if(len5==0)
    {
      printf("[%5d] fake frame: 0x%08x, len5==0\n",ii,buf[ii]); /*there is no payload, probably fake frame*/
      ii++;
      continue;
    }
    padding = (buf[ii]>>23)&0x1;
    printf("[%5d] AIS: 1st word=0x%08x (len5=%d, padding=%d)\n",ii,buf[ii],len5,padding); ii++;
    b16 = (unsigned short *)&buf[ii];
    if(padding==0) nslots = len5 * 2;
    else       nslots = len5 * 2 - 1;
    for(jj=0; jj<nslots; jj++)
    {
      payload = (*b16)&0x1F;
      slot = payload2slot[payload];
      module_id = ((*b16)>>8)&0xF;
      printf("[%5d] Payload[%2d] = %d, slot = %d (module_id=%d, line_id=%d)\n",ii,jj,payload,slot,module_id,((*b16)>>5)&0x3);
      slots[jj] = slot;
      b16 ++;
    }
    ii += len5;

    for(jj=0; jj<nslots; jj++)
    {
      slot_len = buf[ii];
      payload = (buf[ii+1]>>16)&0xFFFF;
      slot = slots[jj];
      printf("   Payload bank length = %d, second word = 0x%08x, payload# %d, slot# %d\n",slot_len,buf[ii+1],payload,slot);
      
      if(module_id==0) /*fadc250*/
      {
        for(kk=2; kk<=slot_len; kk++)
        {
          val = buf[ii+kk];
          q  = (val>> 0) & 0x1FFF;
          ch = (val>>13) & 0x000F;
          t  = ((val>>17) & 0x3FFF) * 4 ;
          printf("   FADC250 Hit[%4d] : slot=%2d ch=%2d t=%6d q=%4d\n",kk-2,slot,ch,t,q);
        }
      }
      else if(module_id==1) /*dcrb*/
      {
        for(kk=2; kk<=slot_len; kk+=2)
        {
  val = buf[ii+kk];
  ch = (val>>29)&0x7;
  pattern_28_00 = val&0x1FFFFFFF;
  val = buf[ii+kk+1];
  pattern_47_29 = val&0x7FFFF;
  t = (val>>19)&0x7FF;
          //printf("   DCRB Hit[%4d] : slot=%2d  channels_group=%2d  t=%6d  pattern_47_29=0x%05x  pattern_28_00=0x%08x\n",kk-2,slot,ch,t,pattern_47_29,pattern_28_00);
  if(ch==0) printf("   DCRB Hit[%5d] : slot=%2d,  time(32ns ticks)=%5d,  pattern for channels 47..00 is 0x%05x%08x\n",kk-2,slot,t,pattern_47_29,pattern_28_00);
  else      printf("   DCRB Hit[%5d] : slot=%2d,  time(32ns ticks)=%5d,  pattern for channels 95..48 is 0x%05x%08x\n",kk-2,slot,t,pattern_47_29,pattern_28_00);
}
      }
      else
      {
printf("UNKNOWN MODULE_ID=%d\n",module_id);
      }
      
      ii += (slot_len+1);
    }

  }
  printf("\nEND OF DATA, ii=%d\n\n",ii);
  
  return(0);
}

