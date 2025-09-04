#!/usr/bin/env python3
import os
import math

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy.stats import skew, kurtosis
import re
from datetime import datetime
import yaml
from pandas import json_normalize

sns.set(style="darkgrid")

# Multiplicative scaling constants
B_b   = 1e1
b_B   = 1/B_b
G_1   = 1e9
one_G = 1/G_1
G_K   = 1e6
K_G   = 1/G_K
G_M   = 1e3
M_G   = 1/G_M
K_1   = 1e3
one_K = 1/K_1
M_1   = 1e6
one_M = 1/M_1
m_1   = 1e-3
one_m = 1/m_1
m_u   = 1e3 
u_m   = 1/m_u
u_1   = 1e-6
one_u = 1/u_1
n_1   = 1e-9
one_n = 1/n_1
n_m   = 1e-6
m_n   = 1/n_m
n_u   = 1e-3
u_n   = 1/n_u

sz1K  = 1024
sz1M  = sz1K*sz1K
sz1G  = sz1M*sz1K
#-----------------------------------------------------

# Compute statistical moments: mean, std, skewness, kurtosis
def compute_moments(series):
    values = series #.dropna()
    return {
        'count': len(values),
        'mean': values.mean(),
        'std': values.std(),
        'skew': skew(values),
        'kurtosis': kurtosis(values)
    }
#-----------------------------------------------------
def moving_average(a, n=5):
    ret = np.cumsum(a, dtype=float)
    ret[n:] = ret[n:] - ret[:-n]
    return ret[n-1:] / n
#-----------------------------------------------------

# Load the log file into a list of lines
def load_log_file(path):
    with open(path, 'r') as f:
        lines = f.readlines()
    return [line.strip() for line in lines if line.strip()]

def gamma_samples(mean, stdev, n_samples, random_state=None):
    """
    Generate n_samples from a Gamma distribution with given mean and stdev.
    
    Parameters:
        mean (float): Desired mean of the distribution
        stdev (float): Desired standard deviation
        n_samples (int): Number of samples to generate
        random_state (int, optional): Random seed for reproducibility
    
    Returns:
        numpy.ndarray: Array of gamma-distributed samples
    """
    rng = np.random.default_rng(random_state)
    
    # Derive shape (k) and scale (theta)
    shape = (mean / stdev) ** 2
    scale = (stdev ** 2) / mean
    
    return rng.gamma(shape, scale, n_samples)
#-----------------------------------------------------
#pip install pyyaml
#-----------------------------------------------------
#Frames actually processed by components
prcsdFrms_df = pd.DataFrame({
    "component":   pd.Series(dtype=int),
    "rcd_uS":      pd.Series(dtype=float),
    "frm_nm":      pd.Series(dtype=int),
    "frm_sz_b":    pd.Series(dtype=float),
    "cmp_ltnc_uS": pd.Series(dtype=float),
    "ntwrk_lt_uS": pd.Series(dtype=float),
    "snt_uS":      pd.Series(dtype=float),
    "done_uS":     pd.Series(dtype=float)
}) # contains no dropped/missed frames

#Frames that components were not ready to receive
drpmsdFrms_df = pd.DataFrame({
    "component":   pd.Series(dtype=int),
    "rcd_uS":      pd.Series(dtype=float),
    "frm_nm":      pd.Series(dtype=int),
    "frm_sz_b":    pd.Series(dtype=float),
    "lstDone_uS":  pd.Series(dtype=float)
}) # contains no dropped/missed frames

#Data on all sent frames - component 0 is source
sentFrms_df = pd.DataFrame({
    "component":   pd.Series(dtype=int),
    "snt_uS":      pd.Series(dtype=float),
    "frm_nm":      pd.Series(dtype=int),
    "frm_sz_b":    pd.Series(dtype=float)
}) 
drpdFrmsFrctn_df = pd.DataFrame({
    "component": pd.Series(dtype=int),
    "drp_frctn":   pd.Series(dtype=int)
})


class RTDP:

    def __init__(self, directory=".", extension=".txt"):
        self.directory = directory
        self.extension = extension

        # Load YAML
        with open("cpu_sim.yaml", "r") as f:
            data = yaml.safe_load(f)

        # Flatten into a DataFrame
        prmtrs_df = json_normalize(data, sep=".")
        print(prmtrs_df.T)  # transpose to make it easier to read

        prm_cmp_ltnc_nS_B   = float(prmtrs_df['cmp_ltnc_nS_B'].iloc[0])
        prm_output_size_GB  = float(prmtrs_df['output_size_GB'].iloc[0])
        prm_nic_Gbps        = float(prmtrs_df['nic_Gbps'].iloc[0])
        prm_daq_frm_sz_MB     = float(prmtrs_df['frame_sz_MB'].iloc[0])
        prm_frame_cnt       = int(prmtrs_df['frame_cnt'].iloc[0])
        prm_cmpnt_cnt       = int(prmtrs_df['cmpnt_cnt'].iloc[0])
        prm_avg_bit_rt_Gbps = float(prmtrs_df['avg_bit_rt_Gbps'].iloc[0])
        #-----------------------------------------------------
        #-----------------------------------------------------
        # Network Latency Samples

        # Target mean and std
        cnst_ntwrk_lt_mean_uS = float(one_u*M_1*prm_daq_frm_sz_MB/(b_B*G_1*prm_nic_Gbps))
        cnst_ntwrk_lt_sd_uS = cnst_ntwrk_lt_mean_uS/3

        # Bound samples to lower bound
        cnst_nl_smpls_uS = gamma_samples(cnst_ntwrk_lt_mean_uS, cnst_ntwrk_lt_sd_uS, int(1e4))

        # Verification
        print("Empirical mean:", np.mean(cnst_nl_smpls_uS))
        print("Empirical std:",  np.std(cnst_nl_smpls_uS))

        # Plot histogram
        plt.figure(figsize=(8, 5))
        plt.hist(cnst_nl_smpls_uS, bins=50, density=True, alpha=0.7, color="blue", edgecolor="black")
        plt.title(f"Clipped Gamma Distribution Samples (mean={cnst_ntwrk_lt_mean_uS}, std={cnst_ntwrk_lt_sd_uS})")
        plt.xlabel("Value")
        plt.ylabel("Density")
        plt.grid(True, linestyle="--", alpha=0.6)
        plt.savefig("Gamma Distribution Samples.png", dpi=300, bbox_inches="tight")  #plt.show()
        #-----------------------------------------------------
        # Sample until > mean Network Latency Samples

        # Target mean and std
        cnst_ntwrk_lt_mean_uS = float(one_u*M_1*prm_daq_frm_sz_MB/(b_B*G_1*prm_nic_Gbps))
        cnst_ntwrk_lt_sd_uS = cnst_ntwrk_lt_mean_uS/3

        # Sample from Gamma
        raw_samples = gamma_samples(cnst_ntwrk_lt_mean_uS, cnst_ntwrk_lt_sd_uS, int(1e4))
        # Bound samples to lower bound
        cnst_nl_smpls_uS = raw_samples[raw_samples >= cnst_ntwrk_lt_mean_uS]

        # Verification
        print("Empirical mean:", np.mean(cnst_nl_smpls_uS))
        print("Empirical std:",  np.std(cnst_nl_smpls_uS))

        # Plot histogram
        plt.figure(figsize=(8, 5))
        plt.hist(cnst_nl_smpls_uS, bins=50, density=True, alpha=0.7, color="blue", edgecolor="black")
        plt.title(f"Clipped Gamma Distribution Samples (mean={cnst_ntwrk_lt_mean_uS}, std={cnst_ntwrk_lt_sd_uS})")
        plt.xlabel("Value")
        plt.ylabel("Density")
        plt.grid(True, linestyle="--", alpha=0.6)
        plt.savefig("Clipped Gamma Distribution Samples.png", dpi=300, bbox_inches="tight")  #plt.show()
        #-----------------------------------------------------
        # Frame Size Samples

        cnst_daq_fs_mean_B = float(M_1*prm_daq_frm_sz_MB)
        # Target mean and std
        cnst_fs_std_B = 0.1*cnst_daq_fs_mean_B

        # Sample from Gamma
        cnst_fs_smpls_B = gamma_samples(cnst_daq_fs_mean_B, cnst_fs_std_B, int(1e4))

        # Verification
        print("Empirical mean:", np.mean(cnst_fs_smpls_B))
        print("Empirical std:",  np.std(cnst_fs_smpls_B))

        # Plot histogram
        plt.figure(figsize=(8, 5))
        plt.hist(cnst_fs_smpls_B, bins=50, density=True, alpha=0.7, color="blue", edgecolor="black")
        plt.title(f"Gamma Frame Size Samples (mean={cnst_daq_fs_mean_B}, std={cnst_fs_std_B})")
        plt.xlabel("Value")
        plt.ylabel("Density")
        plt.grid(True, linestyle="--", alpha=0.6)
        plt.savefig("Gamma Frame Size Samples.png", dpi=300, bbox_inches="tight")  #plt.show()
        #-----------------------------------------------------
        # Out Size Samples

        cnst_os_mean_B = float(G_1*prm_output_size_GB)
        # Target mean and std
        cnst_os_std_B = 0.1*cnst_os_mean_B

        # Sample from Gamma
        os_smpls_B = gamma_samples(cnst_os_mean_B, cnst_os_std_B, int(1e4))

        # Verification
        print("Empirical mean:", np.mean(os_smpls_B))
        print("Empirical std:",  np.std(os_smpls_B))

        # Plot histogram
        plt.figure(figsize=(8, 5))
        plt.hist(os_smpls_B, bins=50, density=True, alpha=0.7, color="blue", edgecolor="black")
        plt.title(f"Component Output Size Samples (mean={cnst_os_mean_B}, std={cnst_os_std_B})")
        plt.xlabel("Value")
        plt.ylabel("Density")
        plt.grid(True, linestyle="--", alpha=0.6)
        plt.savefig("Component Output Size Samples.png", dpi=300, bbox_inches="tight")  #plt.show()
        #-----------------------------------------------------

    def sim(self, filename):

        #set of all frame numbers from sender
        cnst_all_frm_set = set(range(1, prm_frame_cnt + 1))   # range is exclusive at the end, so add 1 for inclusive

        # component 0 is the sender
        clk_uS      = zeros = np.zeros(prm_cmpnt_cnt+1, dtype=float) #Time last frame finished processing

        vrbs = True

        cnst_swtch_lt_uS = 1 #switch latency

        lf = open(filename, "w")

        #Simulation
        for f in range(0, prm_frame_cnt):
            # impulse
        #    if f==prm_frame_cnt/2: prm_nic_Gbps /= 2 #######################################
        #    if f==prm_frame_cnt/2: prm_cmp_ltnc_nS_B *= 2 #######################################
            cnst_daq_frm_sz0_b = B_b*cnst_daq_fs_mean_B; #cnst_fs_smpls_B[f]
            if vrbs: print(f"{clk_uS[0]} Send frame {f} Size: {cnst_daq_frm_sz0_b:10.2f}", file=lf)
            #component zero is the sender
            row = (0,clk_uS[0],f,cnst_daq_frm_sz0_b)
            sentFrms_df = pd.concat([sentFrms_df, pd.DataFrame([row], columns=sentFrms_df.columns)], ignore_index=True)
            for c in range(1, prm_cmpnt_cnt+1):
                #set component forwarding frame size to component output Size
                frm_szc_b = B_b*os_smpls_B[f*prm_cmpnt_cnt+c]
                clk_c = clk_uS[c-1] #temp clk base = upstream senders 'done/sent' value
                # set recvd frame size: cmpnt #1 is senders size, all others are cmpnt output size
                # it is assumed that the sender represents a DAQ with fixed frame size
                # inducing (highly)? variable computational lateny
                if c == 1:
                    frm_sz_b = cnst_daq_frm_sz0_b
                else:
                    frm_sz_b = B_b*os_smpls_B[f*prm_cmpnt_cnt+c-1]

                # component receives with network latency offset from upstream sender time
                cnst_ntwrk_lt_mean_uS = float(one_u*frm_sz_b/(G_1*prm_nic_Gbps))
                cnst_ntwrk_lt_sd_uS = math.ceil(cnst_ntwrk_lt_mean_uS/20) #5%
                ntwrk_lt_uS = 0
                while ntwrk_lt_uS < cnst_ntwrk_lt_mean_uS: #enforce lower bound
                    ntwrk_lt_uS = gamma_samples(cnst_ntwrk_lt_mean_uS, cnst_ntwrk_lt_sd_uS, int(1))[0]

                ntwrk_lt_uS += cnst_swtch_lt_uS  #add switch latency
                clk_c += ntwrk_lt_uS  #Update temp clk for net latency
                rcd_uS = clk_c #Time would recv from upstream sender if ready
                if vrbs: print(f"{clk_c} Component {c} Recv Frame {f} Size: {frm_sz_b:10.2f}", file=lf)
                if (clk_uS[c] > clk_c): # True -> not ready to recv
                    if vrbs: print(f"{clk_c} Component {c} Missed Frame {f}", file=lf)
                    row = (c,rcd_uS,f,frm_sz_b,clk_uS[c])
                    drpmsdFrms_df = pd.concat([drpmsdFrms_df, pd.DataFrame([row], columns=drpmsdFrms_df.columns)], ignore_index=True)
                    break; #If temp clk > components last 'done' time, frame is missed for this and all downstream components
                # component processes with compute latency
                cmp_ltnc_nS_B = gamma_samples(prm_cmp_ltnc_nS_B, prm_cmp_ltnc_nS_B/10, int(1))[0]
                cmp_ltnc_uS = float(n_u*cmp_ltnc_nS_B*frm_sz_b*b_B)
                clk_c += cmp_ltnc_uS #Update temp clk for compute latency
                clk_c += 10 #add overhead
                snt_uS = clk_c
                row = (c,snt_uS,f,frm_szc_b)
                sentFrms_df = pd.concat([sentFrms_df, pd.DataFrame([row], columns=sentFrms_df.columns)], ignore_index=True)
                clk_c += 10 #add overhead
                clk_uS[c]  = clk_c #Set as last 'done' time
                if vrbs and c == prm_cmpnt_cnt: print(f"Update sim clock to {clk_c} for component {c}", file=lf)
                if vrbs: print(f"{clk_c} Component {c} Done Frame {f} Size: {frm_sz_b:10.2f}", file=lf)
                #add prcsdFrms_df row
                row = (c,rcd_uS,f,frm_sz_b,cmp_ltnc_uS,ntwrk_lt_uS,snt_uS,clk_uS[c])
                prcsdFrms_df = pd.concat([prcsdFrms_df, pd.DataFrame([row], columns=prcsdFrms_df.columns)], ignore_index=True)
            # Sender Rate Sleep
            rtSlp_uS   = float(one_u*cnst_daq_frm_sz0_b / (G_1*prm_avg_bit_rt_Gbps))
            clk_uS[0] += rtSlp_uS

        lf.close()   # must close manually    
#-----------------------------------------------------
    def plot_send_bit_rate(self):
        for i in range(0, prm_cmpnt_cnt): #last component does not send

            sim_tm_uS = sentFrms_df.loc[sentFrms_df["component"] == i, "snt_uS"][1:].reset_index(drop=True)
            dts_S = u_1*(sentFrms_df.loc[sentFrms_df["component"] == i, "snt_uS"][1:].reset_index(drop=True) - sentFrms_df.loc[sentFrms_df["component"] == i, "snt_uS"][:-1].reset_index(drop=True))
            szs_b = sentFrms_df.loc[sentFrms_df["component"] == i, "frm_sz_b"][1:].reset_index(drop=True)

            btRt_Mbps = one_M*szs_b/dts_S
            # Display statistics
            btRt_stats_Mbps = compute_moments(btRt_Mbps)

            print(f"Component {i} Send Delta Statistics:")
            for k, v in btRt_stats_Mbps.items():
                print(f"{k}: {v:.3f}")

            # Plot
            plt.figure(figsize=(10, 4))
            if i == 0:
                plt.plot(u_1*sim_tm_uS/60, btRt_Mbps, marker='o', linestyle='-')
            else:
                sns.lineplot(x=u_1*sim_tm_uS/60, y=btRt_Mbps, marker='o')
            plt.title(f"Component {i} Send bit Rate (Mbps)")
            plt.xlabel('Time (minutes)')
            plt.ylabel('Mbps')
            #plt.ylim(0, np.isfinite(recd['fps_roll'].mean()))
            plt.grid(True)
            plt.tight_layout()
            plt.savefig(f"Component {i} Send bit Rate.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
    def plot_rcv_frm_rate(self):
        for i in range(1, prm_cmpnt_cnt + 1): ######################################################################## 1 + 1): # 
            dt_S_arr           = np.diff(u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"])
            frame_rates_Hz_arr = np.array(1/dt_S_arr)

            # Display statistics
            frame_rate_stats = compute_moments(frame_rates_Hz_arr)

            print(f"Component {i} Frame Rate Statistics:")
            for k, v in frame_rate_stats.items():
                print(f"{k}: {v:.3f}")

            # Plot Frame Rate

            plt.figure(figsize=(8, 5))

            timestamps_S_arr = np.array(u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"][1:])
            window = 1
            sns.lineplot(x=timestamps_S_arr[window-1:]/60.0, y=moving_average(frame_rates_Hz_arr, n=window), marker="o")
            # plt.plot(timestamps_S_arr/60.0, frame_rates_Hz_arr, marker='o', linestyle='-')
            # plt.ylim(frame_rates_Hz_arr.min(), max(frame_rates_Hz_arr))
            plt.ticklabel_format(style='plain', axis='y')   # disable scientific/offset notation

            plt.title(f"Component {i} Recv Frame Rate (Hz)")
            plt.xlabel("Minutes")
            plt.ylabel("Hz")
            plt.tight_layout()
            plt.savefig(f"Component {i} Recv Frame Rate.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
# for drop frames tracking - time delta between new recvd and last done (drop if negative)
# OK in your above data frame, i need to calculate all algebraic differences between the columns 'rcd_uS' - 'done_uS' such that the value of 'done_uS' 
# is obtained from the row previous to that of 'rcd_uS' unless the value of 'done_uS' in the same row as 'rcd_uS' is NaN in which case that row is skipped.  
# Under these conditions the next row will find the value of the previous rows value of 'done_uS' will be NaN and so the next more previous values 
# of 'done_uS' should be used to find the most recent non-NaN value for 'done_uS'.
# The first row for each component should be omitted from processing

    def plot_rcv_frm_dlta(self):
        for i in range(1, prm_cmpnt_cnt + 1):

            plt.figure(figsize=(8, 5))

            rcd_uS =  prcsdFrms_df.loc[prcsdFrms_df["component"] == i,  "rcd_uS"][1:].reset_index(drop=True)
            done_uS = prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "done_uS"][:-1].reset_index(drop=True)

            # print(i)
            # print(rcd)
            # print(done)

            delta_mS = u_m*(rcd_uS - done_uS)

            # Display statistics
            delta_stats_mS = compute_moments(delta_mS)

            print(f"Component {i} Recv Delta Statistics:")
            for k, v in delta_stats_mS.items():
                print(f"{k}: {v:.3f}")

            # print(diff)
            # print(df.loc[df["component"] == i, "frm_nm"][1:])
            
            plt.figure(figsize=(8, 5))
            sns.lineplot(x=prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "frm_nm"][1:].reset_index(drop=True), y=delta_mS , marker="o")       # line + markers
            plt.title(f"Component {i} Recv Frame Delta")
        #    plt.xlabel("Minutes")
            plt.xlabel("Frame")
            plt.ylabel("mS")
            plt.tight_layout()
            plt.savefig(f"Component {i} Recv Frame Delta.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
    def plot_rcv_recv_bit_rate(self):
        for i in range(1, prm_cmpnt_cnt + 1):

            sim_tm_uS = prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"][1:].reset_index(drop=True)
            dts_S = u_1*(prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"][1:].reset_index(drop=True) - prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"][:-1].reset_index(drop=True))
            szs_b = prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "frm_sz_b"][1:].reset_index(drop=True)

            btRt_Mbps = one_M*szs_b/dts_S
            # Display statistics
            btRt_stats_Mbps = compute_moments(btRt_Mbps)

            print(f"Component {i} Recv Delta Statistics:")
            for k, v in btRt_stats_Mbps.items():
                print(f"{k}: {v:.3f}")

            # Plot
            plt.figure(figsize=(10, 4))
            # plt.plot(u_1*sim_tm_uS/60, btRt_Mbps, marker='o', linestyle='-')
            sns.lineplot(x=u_1*sim_tm_uS/60, y=btRt_Mbps, marker='o', linestyle='-')
            plt.title(f"Component {i} Recv bit Rate (Mbps)")
            plt.xlabel('Time (minutes)')
            plt.ylabel('Mbps')
            #plt.ylim(0, np.isfinite(recd['fps_roll'].mean()))
            plt.grid(True)
            plt.tight_layout()
            plt.savefig(f"Component {i} Recv bit Rate.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
    def plot_cmp_ltnc(self):
        for i in range(1, prm_cmpnt_cnt + 1):

            cmpLt_mS = u_m*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "cmp_ltnc_uS"]
            # Display statistics
            cmpLt_stats_mS = compute_moments(cmpLt_mS)

            print(f"Component {i} Recv Delta Statistics:")
            for k, v in cmpLt_stats_mS.items():
                print(f"{k}: {v:.3f}")

            # Plot
            plt.figure(figsize=(10, 4))
            # plt.plot(u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"]/60, cmpLt_mS, marker='o', linestyle='-')
            sns.lineplot(x=u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"]/60, y=cmpLt_mS, marker='o', linestyle='-')
            plt.title(f"Component {i} Comp Latency (mS)")
            plt.xlabel('Time (minutes)')
            plt.ylabel('Latency (mS)')
            #plt.ylim(0, np.isfinite(recd['fps_roll'].mean()))
            plt.grid(True)
            plt.tight_layout()
            plt.savefig(f"Component {i} Comp Latency.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
    def plot_ntwrk_ltnc(self):
        for i in range(1, prm_cmpnt_cnt + 1):

            ntwrkLt_uS = prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "ntwrk_lt_uS"]
            # Display statistics
            ntwrkLt_stats_uS = compute_moments(ntwrkLt_uS)

            print(f"Component {i} Recv Delta Statistics:")
            for k, v in ntwrkLt_stats_uS.items():
                print(f"{k}: {v:.3f}")

            # Plot
            plt.figure(figsize=(10, 4))
            # plt.plot(u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"]/60, ntwrkLt_uS, marker='o', linestyle='-')
            sns.lineplot(x=u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"]/60, y=ntwrkLt_uS, marker='o', linestyle='-')
            plt.title(f"Component {i} Network Latency (uS)")
            plt.xlabel('Time (minutes)')
            plt.ylabel('Latency (uS)')
            #plt.ylim(0, np.isfinite(recd['fps_roll'].mean()))
            plt.grid(True)
            plt.tight_layout()
            plt.savefig(f"Component {i} Network Latency.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
    def plot_frm_rcv(self):
        for i in range(1, prm_cmpnt_cnt + 1):
            
            # Plot
            plt.figure(figsize=(10, 4))
            plt.plot(u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"]/60, prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "frm_nm"], marker='o', linestyle='-')
            # sns.lineplot(x=u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"]/60, y=prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "frm_nm"], marker='o')
            plt.title(f"Component {i} Frame Recption Over Time")
            plt.xlabel('Time (Minutes)')
            plt.ylabel('recd (Frame num)')
            plt.ylim(0, max(prcsdFrms_df['frm_nm']) * 1.2)
            plt.grid(True)
            plt.tight_layout()
            plt.savefig(f"Component {i} Frame Recption Over Time.png", dpi=300, bbox_inches="tight")  #plt.show()
#-----------------------------------------------------
    def plot_tm_frm_rcv(self):
        for i in range(1, prm_cmpnt_cnt + 1):
            # Plot
            plt.plot(u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "rcd_uS"]/60, prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "frm_nm"], marker='o', linestyle='-')
            
        # Add labels and legend
        plt.xlabel('Sim Time (Mins)')
        plt.ylabel('Frame Number')
        plt.title('Time Frame Recvd')

        # Show grid and plot
        plt.grid(True)
        plt.tight_layout()
        plt.savefig("Time Frame Recvd.png", dpi=300, bbox_inches="tight")  #plt.show()
#-----------------------------------------------------
    def plot_tm_frm_dn(self):
        for i in range(1, prm_cmpnt_cnt + 1):
            # Plot
            plt.plot(u_1*prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "done_uS"]/60, prcsdFrms_df.loc[prcsdFrms_df["component"] == i, "frm_nm"], marker='o', linestyle='-')
            
        # Add labels and legend
        plt.xlabel('Sim Time (Hrs)')
        plt.ylabel('Frame Number')
        plt.title('Time Frame Done')

        # Show grid and plot
        plt.grid(True)
        plt.tight_layout()
        plt.savefig("Time Frame Done.png", dpi=300, bbox_inches="tight")  #plt.show()
#-----------------------------------------------------
    def plot_drops(self):
        drp_sets_by_component = drpmsdFrms_df.groupby("component")["frm_nm"].apply(set)
        cmpnt_drp_nms = set(drpmsdFrms_df["component"].unique())
        for c in cmpnt_drp_nms:
            print(f"Number of drops for component {c}: {len(drp_sets_by_component[c])}")
#-----------------------------------------------------
    def plot_rp_seta(self):
        rp_sets_by_component
#-----------------------------------------------------
    def plot_drp_nms(self):
        cmpnt_drp_nms
#-----------------------------------------------------
    def plot_rcv_frm_dlta(self):
        prcsd_frm_sets_by_component = prcsdFrms_df.groupby("component")["frm_nm"].apply(set)
        cmpnt_frn_nms = set(prcsdFrms_df["component"].unique())
        for c in cmpnt_frn_nms:
            print(f"Number of processed frames for component {c}: {len(prcsd_frm_sets_by_component[c])}")
#-----------------------------------------------------
    def plot_drps_mss(self):
        cmpnt_drp_nms = set(drpmsdFrms_df["component"].unique())
        #dataframe record for components with no drops
        for c in set(range(1,prm_cmpnt_cnt+1)) - cmpnt_drp_nms: #the set of compnents with no drops
            row = (c,0)
            drpdFrmsFrctn_df = pd.concat([drpdFrmsFrctn_df, pd.DataFrame([row], columns=drpdFrmsFrctn_df.columns)], ignore_index=True)
            
        #dataframe record for components with drops
        drp_sets_by_component = drpmsdFrms_df.groupby("component")["frm_nm"].apply(set)
        for c in cmpnt_drp_nms:
            row = (c,len(drp_sets_by_component[c])/(len(drp_sets_by_component[c]) + len(prcsd_frm_sets_by_component[c])))
            drpdFrmsFrctn_df = pd.concat([drpdFrmsFrctn_df, pd.DataFrame([row], columns=drpdFrmsFrctn_df.columns)], ignore_index=True)


        # drpdFrmsFrctn_df

        # Plot
        from matplotlib.ticker import MaxNLocator

        x = drpdFrmsFrctn_df['component'].astype(int)
        y = 100*drpdFrmsFrctn_df['drp_frctn']
        max_y = np.nanmax(y) if len(y) else 1.0

        fig, ax = plt.subplots(figsize=(10, 4))
        ax.plot(x, y, marker='o', linestyle='-')
        ax.set_title("Component Drops")
        ax.set_xlabel("Component")
        ax.set_ylabel("Percent")
        # ax.set_ylim(-0.01, max_y * 1.2)

        # Force integer tick locations
        ax.xaxis.set_major_locator(MaxNLocator(integer=True))

        ax.grid(True)
        fig.tight_layout()
        plt.savefig("Component Drops.png", dpi=300, bbox_inches="tight")  #plt.show()

        #Frames that components missed because they were never received
        msdFrmsFrctn_df = pd.DataFrame({
            "component": pd.Series(dtype=int),
            "msd_frctn": pd.Series(dtype=int)
        })

        #Compnents miss frames when upstream senders do not send
        #Upstrean components do not send frames they miss or drop
        #The effect is cummulative for downstream components

        #For each component, This equates to the number of sender frames - (number processd + the number droped (since they were not missed))

        for c in range(1,prm_cmpnt_cnt+1):
            # print(f"drpdFrmsFrctn_df for component {c}: {drpdFrmsFrctn_df.loc[drpdFrmsFrctn_df["component"] == (c-1), "drp_frctn"]}")
            # row = (c,1-(len(prcsd_frm_sets_by_component[c]) + len(set(drpmsdFrms_df.loc[drpmsdFrms_df["component"] == c, "frm_nm"])))/len(cnst_all_frm_set))
            row = (c,1-(len(set(prcsdFrms_df.loc[prcsdFrms_df["component"] == c, "frm_nm"])) + len(set(drpmsdFrms_df.loc[drpmsdFrms_df["component"] == c, "frm_nm"])))/len(cnst_all_frm_set))
            msdFrmsFrctn_df = pd.concat([msdFrmsFrctn_df, pd.DataFrame([row], columns=msdFrmsFrctn_df.columns)], ignore_index=True)

        x = msdFrmsFrctn_df['component'].astype(int)
        y = 100*msdFrmsFrctn_df['msd_frctn']
        ax_y = np.nanmax(y) if len(y) else 1.0

        fig, ax = plt.subplots(figsize=(10, 4))
        ax.plot(x, y, marker='o', linestyle='-')
        ax.set_title("Component Misses")
        ax.set_xlabel("Component")
        ax.set_ylabel("Percent")
        # ax.set_ylim(-0.01, max_y * 1.2)

        # Force integer tick locations
        ax.xaxis.set_major_locator(MaxNLocator(integer=True))

        ax.grid(True)
        fig.tight_layout()
        plt.savefig("Component Misses.png", dpi=300, bbox_inches="tight")  #plt.show()
#-----------------------------------------------------
# import os

# class BatchProcessor:
#     def __init__(self, directory=".", extension=".txt"):
#         self.directory = directory
#         self.extension = extension

#     def process_file(self, filename):
#         """Define what to do with each file here"""
#         print(f"Processing {filename}")
#         # Example work:
#         with open(os.path.join(self.directory, filename)) as f:
#             first_line = f.readline().strip()
#         print(f"  First line: {first_line}")

#     def run(self):
#         """Loop over files and process them"""
#         for f in os.listdir(self.directory):
#             if f.endswith(self.extension):
#                 self.process_file(f)

# Run script
if __name__ == "__main__":
    processor = RTDP(directory=".", extension=".txt")
    RTDP.sim("output.txt")

#class MyClass:
#    def say_hello(self):
#        print("Hello from", self)

#obj = MyClass()
#obj.say_hello()

# [goodrich@goodrich-rhel7 cpu_emu]$ python
# Python 3.6.8 (default, Nov 15 2024, 08:11:39) 
# [GCC 8.5.0 20210514 (Red Hat 8.5.0-22)] on linux
# Type "help", "copyright", "credits" or "license" for more information.
# >>> from rtdp import RTDP
# >>> obj = RTDP()
#                          0
# cmp_ltnc_nS_B    500.00000
# output_size_GB     0.00006
# nic_Gbps         100.00000
# frame_sz_MB        0.06000
# frame_cnt        100.00000
# cmpnt_cnt          7.00000
# avg_bit_rt_Gbps    0.01500
# Empirical mean: 6.020194524041597
# Empirical std: 1.998842895413426
# Empirical mean: 7.7762441952706745
# Empirical std: 1.5377529432810715
# Empirical mean: 60043.20662323206
# Empirical std: 5987.646220164829
# Empirical mean: 60022.31002681512
# Empirical std: 5998.009136292724
# >>> obj.sim("output.txt")
# Traceback (most recent call last):
#   File "<stdin>", line 1, in <module>
#   File "/home/goodrich/src/SRO-RTDP/rtdp/cpp/cpu_emu/rtdp.py", line 245, in sim
#     cnst_all_frm_set = set(range(1, prm_frame_cnt + 1))   # range is exclusive at the end, so add 1 for inclusive
# NameError: name 'prm_frame_cnt' is not defined

