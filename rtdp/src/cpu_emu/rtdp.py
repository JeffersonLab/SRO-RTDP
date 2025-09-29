#!/usr/bin/env python3
import os
import math
import random

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
import re
import yaml
import subprocess
#import pexpect
import shlex
import random

from typing import Optional, Union, List
from datetime import datetime
from scipy.stats import skew, kurtosis
from pandas import json_normalize

sns.set(style="darkgrid")
#-----------------------------------------------------
#-----------------------------------------------------

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

# Load the log file into a list of lines
def load_log_file(path):
    with open(path, 'r') as f:
        lines = f.readlines()
    return [line.strip() for line in lines if line.strip()]
#-----------------------------------------------------
import subprocess

def launch_remote(ip, cmd, prog):
    """
    Copy receiver program to remote host and launch it asynchronously.
    cmd[0] = receiver program, cmd[1:] = arguments
    """
    receiver = prog
    args = cmd[1:]
    rnd_tag = random.randint(0, 9999)
    prog_rn = f"{receiver}{rnd_tag}"
    
    try:
        # Step 1: Copy receiver to remote host
        scp_cmd = ["scp", receiver, f"{ip}:~/{prog_rn}"]
        print(f"[INFO] Copying {receiver} to {ip}:~/{prog_rn}...", flush=True)
        subprocess.run(scp_cmd, check=True)  # OTP prompt
        print(f"[INFO] SCP Success...", flush=True)

    except subprocess.CalledProcessError as e:
        print(f"[ERROR] SCP failed: {e}", flush=True)
        return None
    except Exception as e:
        print(f"[ERROR] Unexpected error during SCP: {e}", flush=True)
        return None

    try:
        # Step 2: Build remote command
        remote_cmd = f"chmod +x ~/{prog_rn} && nohup ~/{prog_rn} {' '.join(args)} > ~/{prog_rn}.out 2>&1 &"
        ssh_cmd = ["ssh", ip, remote_cmd]

        print(f"[INFO] Launching {prog_rn} on {ip}...", flush=True)
        # Launch asynchronously, keep stdin open for OTP
        process = subprocess.Popen(ssh_cmd)
        print(f"[INFO] {prog_rn} launched on {ip}. Continuing Python script...", flush=True)
        return process

    except Exception as e:
        print(f"[ERROR] Failed to launch remote process: {e}", flush=True)
        return None

#-----------------------------------------------------
#-----------------------------------------------------

def launch_emulate(ip, cmd, prog):
    """
    Emulate a receiver on a remote host.
    """
    print(f"[INFO] Starting emulation for {cmd[0]} on {ip}...", flush=True)

    process = launch_remote(ip, cmd, prog)

    if process is None:
        print("[WARN] Remote launch failed. Emulation did not start.", flush=True)
    else:
        print("[INFO] Remote emulation started successfully.", flush=True)

    print("(End of launch_emulate)", flush=True)

#-----------------------------------------------------
#-----------------------------------------------------

def bernoulli(p: float, n: int = 1, rng: Optional[Union[random.Random, np.random.Generator]] = None) -> List[int]:
    """
    Bernoulli sampler using a provided RNG (Python random.Random or NumPy Generator).
    
    Args:
        p (float): Probability of success, 0 <= p <= 1.
        n (int): Number of trials.
        rng (random.Random or np.random.Generator, optional): RNG instance to use.
            If None, uses Python's default random module.
    
    Returns:
        list[int]: List of 0s and 1s representing Bernoulli trials.
    """
    if not (0 <= p <= 1):
        raise ValueError("Probability p must be between 0 and 1.")

    # Python random
    if rng is None or isinstance(rng, random.Random):
        rng = rng or random
        return [1 if rng.random() < p else 0 for _ in range(n)]
    
    # NumPy Generator
    elif isinstance(rng, np.random.Generator):
        return (rng.random(n) < p).astype(int).tolist()
    
    else:
        raise TypeError("rng must be either random.Random or np.random.Generator")

#-----------------------------------------------------
# Compute statistical moments: mean, std, skewness, kurtosis
def compute_moments(series):
    """
    report series length and first four statistical moments

    Parameters
    ----------
    series : indexable

    Returns
    -------
        series length and first four statistical moments
    """
    values = series #.dropna()
    return {
        'count': len(values),
        'mean': values.mean(),
        'std': values.std(),
        'skew': skew(values),
        'kurtosis': kurtosis(values)
    }
#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
# Compute Moving Avg
def moving_average(a, n=5):
    """
    moving average for window size

    Parameters
    ----------
    a : indexable
    n : str, optional, window size

    Returns
    -------
        reduced size (by n) of same input type
    """
    ret = np.cumsum(a, dtype=float)
    ret[n:] = ret[n:] - ret[:-n]
    return ret[n-1:] / n
#-----------------------------------------------------

#-----------------------------------------------------
## --------------------
# Example usage
# --------------------

# # Using Python random.Random
# py_rng = random.Random(42)
# print(bernoulli(0.001, n=10, rng=py_rng))

# # Using NumPy Generator
# np_rng = np.random.default_rng(42)
# print(bernoulli(0.001, n=10, rng=np_rng))


#-----------------------------------------------------
#-----------------------------------------------------
class RTDP:
    def __del__(self):
        """
        destructor

        Parameters
        ----------
            none

        Returns
        -------
            none

        """
        self.log_file.close()

#-----------------------------------------------------
#-----------------------------------------------------
    def __init__(self, rng_seed = None, directory=".", extension=".txt", log_file="logfile.txt"):
        """
        initializer for implicit constructor

        Parameters
        ----------
        rng_seed : int or None
        directory : str, optional
            Path to the directory to search. Defaults to the current
            working directory (``"."``).
        extension : str, optional
            File extension to match. Defaults to ``".txt"``.
        log_file : str, optional
            File that simulate/emulate will write the execution trace.
        sim_config : str, optional
            Run Parameters.

        Returns
        -------
            none
        """

        # Process initializer input parms
        self.rng       = np.random.default_rng(rng_seed)
        self.directory = directory
        self.extension = extension
        self.log_file  = open(log_file, "w")

        # Frames actually processed by components
        self.prcsdFrms_df = pd.DataFrame({
            "component":   pd.Series(dtype=int),
            "rcd_uS":      pd.Series(dtype=float),
            "frm_nm":      pd.Series(dtype=int),
            "frm_sz_b":    pd.Series(dtype=float),
            "cmp_ltnc_uS": pd.Series(dtype=float),
            "ntwrk_lt_uS": pd.Series(dtype=float),
            "snt_uS":      pd.Series(dtype=float),
            "done_uS":     pd.Series(dtype=float)
        }) # contains no dropped/missed frames

        # Frames that components were not ready to receive
        self.drpmsdFrms_df = pd.DataFrame({
            "component":   pd.Series(dtype=int),
            "rcd_uS":      pd.Series(dtype=float),
            "frm_nm":      pd.Series(dtype=int),
            "frm_sz_b":    pd.Series(dtype=float),
            "lstDone_uS":  pd.Series(dtype=float)
        }) # contains dropped frames

        # Data on all sent frames - component 0 is source
        self.sentFrms_df = pd.DataFrame({
            "component":   pd.Series(dtype=int),
            "snt_uS":      pd.Series(dtype=float),
            "frm_nm":      pd.Series(dtype=int),
            "frm_sz_b":    pd.Series(dtype=float)
        })

        # Dropped Frames Info
        self.drpdFrmsFrctn_df = pd.DataFrame({
            "component": pd.Series(dtype=int),
            "drp_frctn":   pd.Series(dtype=int)
        })

#-----------------------------------------------------
    def gen_gamma_samples(self, mean, stdev, n_samples):
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

        # Derive shape (k) and scale (theta)
        shape = (mean / stdev) ** 2
        scale = (stdev ** 2) / mean

        return self.rng.gamma(shape, scale, n_samples)

#-----------------------------------------------------
#-----------------------------------------------------
    def sim(self, sim_config="cpu_sim.yaml"):
        """
        simulate component daisy chain

        Parameters
        ----------
            none

        Returns
        -------
            none
        """
        # Process config yaml file
        self.sim_config= sim_config
        try:
            with open(self.sim_config, "r") as f:
                data = yaml.safe_load(f)
            print(f"[INFO] Loaded config from {self.sim_config}")
        except Exception as e:
            print(f"[ERROR] Failed to load YAML config: {e}")
            return

        # Flatten into a DataFrame
        sim_prmtrs_df = json_normalize(data, sep=".")
        print(sim_prmtrs_df.T, file=self.log_file)  # transpose to make it easier to read

        self.sim_prm_cmp_ltnc_nS_B      = float(sim_prmtrs_df['cmp_ltnc_nS_B'].iloc[0])
        self.sim_prm_output_size_GB     = float(sim_prmtrs_df['output_size_GB'].iloc[0])
        self.sim_prm_nic_Gbps           = float(sim_prmtrs_df['nic_Gbps'].iloc[0])
        self.sim_prm_daq_frm_sz_MB      = float(sim_prmtrs_df['frame_sz_MB'].iloc[0])
        self.sim_prm_frame_cnt          =   int(sim_prmtrs_df['frame_cnt'].iloc[0])
        self.sim_prm_cmpnt_cnt          =   int(sim_prmtrs_df['cmpnt_cnt'].iloc[0])
        self.sim_prm_avg_bit_rt_Gbps    = float(sim_prmtrs_df['avg_bit_rt_Gbps'].iloc[0])
        #------------------------ setup plots for simulation run ------------------------
        self.prm_cmpnt_cnt              = self.sim_prm_cmpnt_cnt        
        #set of all frame numbers from sender
        self.cnst_all_frm_set = set(range(1, self.sim_prm_frame_cnt + 1))   # range is exclusive at the end, so add 1 for inclusive
        #-----------------------------------------------------

        # Network Latency Samples

        # Target mean and std
        ntwrk_lt_mean_uS = float(one_u*M_1*self.sim_prm_daq_frm_sz_MB/(b_B*G_1*self.sim_prm_nic_Gbps))
        ntwrk_lt_sd_uS = ntwrk_lt_mean_uS/3

        # Bound samples to lower bound
        cnst_nl_smpls_uS = self.gen_gamma_samples(ntwrk_lt_mean_uS, ntwrk_lt_sd_uS, int(1e4))

        # Verification
        nl_stats_uS = compute_moments(cnst_nl_smpls_uS)

        print(f"DAQ (Component {0}) Network Latency Statistics (uS):", file=self.log_file)
        for k, v in nl_stats_uS.items():
            print(f"{k}: {v:.3f}", file=self.log_file)

        # Plot histogram
        plt.figure(figsize=(8, 5))
        plt.hist(cnst_nl_smpls_uS, bins=50, density=True, alpha=0.7, color="blue", edgecolor="black")
        plt.title(f"Network Latency Gamma Distribution Samples (mean={ntwrk_lt_mean_uS}, std={ntwrk_lt_sd_uS})")
        plt.xlabel("Value")
        plt.ylabel("Density")
        plt.grid(True, linestyle="--", alpha=0.6)
        plt.savefig("NtwrkGmmDstrbnSmpls.png", dpi=300, bbox_inches="tight")  #plt.show()
        #-----------------------------------------------------
        # Sample until > mean Network Latency Samples

        # Target mean and std
        ntwrk_lt_mean_uS = float(one_u*M_1*self.sim_prm_daq_frm_sz_MB/(b_B*G_1*self.sim_prm_nic_Gbps))
        ntwrk_lt_sd_uS = ntwrk_lt_mean_uS/3

        # Sample from Gamma
        raw_samples = self.gen_gamma_samples(ntwrk_lt_mean_uS, ntwrk_lt_sd_uS, int(1e4))
        # Bound samples to lower bound
        cnst_nl_smpls_uS = raw_samples[raw_samples >= ntwrk_lt_mean_uS]

        # Verification

        nl_stats_uS = compute_moments(cnst_nl_smpls_uS)
        print(f"DAQ (Component {0}) Network Latency Statistics with lower bound (uS):", file=self.log_file)
        for k, v in nl_stats_uS.items():
            print(f"{k}: {v:.3f}", file=self.log_file)

        # Plot histogram
        plt.figure(figsize=(8, 5))
        plt.hist(cnst_nl_smpls_uS, bins=50, density=True, alpha=0.7, color="blue", edgecolor="black")
        plt.title(f"Clipped Network Latency Gamma Distribution Samples (mean={ntwrk_lt_mean_uS}, std={ntwrk_lt_sd_uS})")
        plt.xlabel("Value")
        plt.ylabel("Density")
        plt.grid(True, linestyle="--", alpha=0.6)
        plt.savefig("ClpdNtwrkGmmDstrbnSmpls.png", dpi=300, bbox_inches="tight")  #plt.show()
        #-----------------------------------------------------
        # Frame Size Samples

        self.cnst_daq_fs_mean_B = float(M_1*self.sim_prm_daq_frm_sz_MB)
        # Target mean and std
        cnst_fs_std_B = 0.1*self.cnst_daq_fs_mean_B

        # Sample from Gamma
        cnst_fs_smpls_B = self.gen_gamma_samples(self.cnst_daq_fs_mean_B, cnst_fs_std_B, int(1e4))

        # Verification
        cnst_fs_stats_B = compute_moments(cnst_fs_smpls_B)
        print(f"DAQ (Component {0}) Frame Size Statistics (B):", file=self.log_file)
        for k, v in cnst_fs_stats_B.items():
            print(f"{k}: {v:.3f}", file=self.log_file)

        # Plot histogram
        plt.figure(figsize=(8, 5))
        plt.hist(cnst_fs_smpls_B, bins=50, density=True, alpha=0.7, color="blue", edgecolor="black")
        plt.title(f"Frame Size Gamma Samples (mean={self.cnst_daq_fs_mean_B}, std={cnst_fs_std_B})")
        plt.xlabel("Value")
        plt.ylabel("Density")
        plt.grid(True, linestyle="--", alpha=0.6)
        plt.savefig("FrmSzGmmSmpls.png", dpi=300, bbox_inches="tight")  #plt.show()
        #-----------------------------------------------------
        # Out Size Samples

        cnst_os_mean_B = float(G_1*self.sim_prm_output_size_GB)
        # Target mean and std
        cnst_os_std_B = 0.1*cnst_os_mean_B

        # Sample from Gamma
        self.os_smpls_B = self.gen_gamma_samples(cnst_os_mean_B, cnst_os_std_B, int(1e4))

        # Verification
        os_stats_B = compute_moments(self.os_smpls_B)
        print(f"Component Output Size Statistics: (B)", file=self.log_file)
        for k, v in os_stats_B.items():
            print(f"{k}: {v:.3f}", file=self.log_file)

        # Plot histogram
        plt.figure(figsize=(8, 5))
        plt.hist(self.os_smpls_B, bins=50, density=True, alpha=0.7, color="blue", edgecolor="black")
        plt.title(f"Component Output Size Samples (mean={cnst_os_mean_B}, std={cnst_os_std_B})")
        plt.xlabel("Value")
        plt.ylabel("Density")
        plt.grid(True, linestyle="--", alpha=0.6)
        plt.savefig("CmpntOtptSzSmpls.png", dpi=300, bbox_inches="tight")  #plt.show()
        #-----------------------------------------------------

        # set all component clocks to zero; component 0 is the sender
        clk_uS = np.zeros(self.sim_prm_cmpnt_cnt+1, dtype=float) #Time last frame finished processing

        vrbs = True

        cnst_swtch_lt_uS = 1 #switch latency

        clib = bernoulli(0.01, n=self.sim_prm_frame_cnt, rng=self.rng) #impulse boolean with given % probability of success
        nlib = bernoulli(0.01, n=self.sim_prm_frame_cnt, rng=self.rng) #impulse boolean with given % probability of success
        
        if vrbs: print("Simulating ...")
        #if vrbs: print(f"ib =  {ib}", file=self.log_file)

        #Simulation
        #reset dataframes
        self.sentFrms_df        = self.sentFrms_df.iloc[0:0]
        self.drpmsdFrms_df      = self.drpmsdFrms_df.iloc[0:0]
        self.prcsdFrms_df       = self.prcsdFrms_df.iloc[0:0]
        self.drpdFrmsFrctn_df   = self.drpdFrmsFrctn_df.iloc[0:0]
        
        for f in range(0, self.sim_prm_frame_cnt):
            # impulses
            if clib[f]==1: # computational latency
                brn_trl = bernoulli(0.5, n=1, rng=self.rng) # coin toss
                self.sim_prm_cmp_ltnc_nS_B *= 1 + (0.2 if brn_trl[0]==1 else -(1-1/1.2)) # random effect
                if vrbs: print(f"{clk_c} Impulse: Compute Latency (ns/B) now at {self.sim_prm_cmp_ltnc_nS_B:10.2f} frame {f}", file=self.log_file)
            if nlib[f]==1: # network latency
                brn_trl = bernoulli(0.5, n=1, rng=self.rng) # coin toss
                self.sim_prm_nic_Gbps *= 1 + (0.2 if brn_trl[0]==1 else -(1-1/1.2)) # random effect
                if vrbs: print(f"{clk_c} Impulse: Network Speed (Gbps) now at {self.sim_prm_nic_Gbps:10.2f} frame {f}", file=self.log_file)
            cnst_daq_frm_sz0_b = B_b*self.cnst_daq_fs_mean_B; #cnst_fs_smpls_B[f]
            if vrbs: print(f"{clk_uS[0]} Send frame {f} Size (b): {cnst_daq_frm_sz0_b:10.2f}", file=self.log_file)
            #component zero is the sender
            row = (0,clk_uS[0],f,cnst_daq_frm_sz0_b)
            self.sentFrms_df = pd.concat([self.sentFrms_df, pd.DataFrame([row], columns=self.sentFrms_df.columns)], ignore_index=True)
            for c in range(1, self.sim_prm_cmpnt_cnt+1):
                #set component forwarding frame size to component output Size
                frm_szc_b = B_b*self.os_smpls_B[f*self.sim_prm_cmpnt_cnt+c]
                clk_c = clk_uS[c-1] #temp clk base = upstream senders 'done/sent' value
                # set recvd frame size: cmpnt #1 is senders size, all others are cmpnt output size
                # it is assumed that the sender represents a DAQ with fixed frame size
                # inducing (highly)? variable computational lateny
                if c == 1:
                    frm_sz_b = cnst_daq_frm_sz0_b
                else:
                    frm_sz_b = B_b*self.os_smpls_B[f*self.sim_prm_cmpnt_cnt+c-1]

                # component receives with network latency offset from upstream sender time
                ntwrk_lt_mean_uS = float(one_u*frm_sz_b/(G_1*self.sim_prm_nic_Gbps))
                ntwrk_lt_sd_uS = math.ceil(ntwrk_lt_mean_uS/20) #5%
                ntwrk_lt_uS = 0
                while ntwrk_lt_uS < ntwrk_lt_mean_uS: #enforce lower bound
                    ntwrk_lt_uS = self.gen_gamma_samples(ntwrk_lt_mean_uS, ntwrk_lt_sd_uS, int(1))[0]

                ntwrk_lt_uS += cnst_swtch_lt_uS  #add switch latency
                clk_c += ntwrk_lt_uS  #Update temp clk for net latency
                rcd_uS = clk_c #Time would recv from upstream sender if ready
                if vrbs: print(f"{clk_c} Component {c} Recv Frame {f} Size (b): {frm_sz_b:10.2f}", file=self.log_file)
                #If temp clk < components last 'done' time, frame is dropped for this component and missed by all downstream components
                if (clk_uS[c] > clk_c): # True -> not ready to recv
                    if vrbs: print(f"{clk_c} Component {c} Dropped Frame {f}", file=self.log_file)
                    row = (c,rcd_uS,f,frm_sz_b,clk_uS[c])
                    self.drpmsdFrms_df = pd.concat([self.drpmsdFrms_df, pd.DataFrame([row], columns=self.drpmsdFrms_df.columns)], ignore_index=True)
                    break; # All downstream components will miss this frame
                # component processes with compute latency
                cmp_ltnc_nS_B = self.gen_gamma_samples(self.sim_prm_cmp_ltnc_nS_B, self.sim_prm_cmp_ltnc_nS_B/10, int(1))[0]
                cmp_ltnc_uS = float(n_u*cmp_ltnc_nS_B*frm_sz_b*b_B)
                clk_c += cmp_ltnc_uS #Update temp clk for compute latency
                clk_c += 10 #add overhead
                snt_uS = clk_c
                row = (c,snt_uS,f,frm_szc_b)
                self.sentFrms_df = pd.concat([self.sentFrms_df, pd.DataFrame([row], columns=self.sentFrms_df.columns)], ignore_index=True)
                clk_c += 10 #add overhead
                clk_uS[c]  = clk_c #Set as last 'done' time
                #if vrbs and c == self.sim_prm_cmpnt_cnt: print(f"Update sim clock to {clk_c} (uS) for component {c}", file=self.log_file)
                if vrbs: print(f"{clk_c} Component {c} Done Frame {f} Size (b): {frm_sz_b:10.2f}", file=self.log_file)
                #add self.prcsdFrms_df row
                row = (c,rcd_uS,f,frm_sz_b,cmp_ltnc_uS,ntwrk_lt_uS,snt_uS,clk_uS[c])
                self.prcsdFrms_df = pd.concat([self.prcsdFrms_df, pd.DataFrame([row], columns=self.prcsdFrms_df.columns)], ignore_index=True)
            # Sender Rate Sleep
            rtSlp_uS   = float(one_u*cnst_daq_frm_sz0_b / (G_1*self.sim_prm_avg_bit_rt_Gbps))
            clk_uS[0] += rtSlp_uS

#-----------------------------------------------------



#-----------------------------------------------------
#-----------------------------------------------------
    def emulate(self, config="emulate.yaml", prog="cpu_emu"):
        """
        setup component daisy chain

        Parameters
        ----------
        file : str, optional
            Path to emulation run parameters
        prog : str, optional
            Path to binary object to use for emulation

        Returns
        -------
            none
        """

        # parse config file
        try:
            with open(config, "r") as f:
                emu_setup_prms = yaml.safe_load(f)
            print(f"[INFO] Loaded config from {config}", flush=True)
        except Exception as e:
            print(f"[ERROR] Failed to load YAML config: {e}", flush=True)
            return

        
        try:
            frm_sz_MB       = emu_setup_prms["frm_sz_MB"]
            frm_cnt         = emu_setup_prms["frm_cnt"]
            avg_bit_rt_Gbps = emu_setup_prms["avg_bit_rt_Gbps"]
            verbosity       = emu_setup_prms["verbosity"]
            base_port       = emu_setup_prms["base_port"]
            sender          = emu_setup_prms["sender"]
            host_ip_list    = emu_setup_prms.get("hosts", [])
        except KeyError as e:
            print(f"[ERROR] Missing required config key: {e}", flush=True)
            return

        if(len(host_ip_list) < 1):
            print(f"[ERROR] Incorrect hosts list in config file", flush=True)
            return

        sender_ip_list = [sender] + host_ip_list
        
        current_p = base_port
        current_r = base_port + 1

        # setup and deploy components
        # for idx, ip in enumerate(host_ip_list[1:], start=1):
        
        remote_log = f"~/{prog}.log"
        prog_tags = []

        for idx, ip in enumerate(host_ip_list):

            z_val = 1 if idx == (len(host_ip_list) - 1) else 0

            cmd = [
                f"~/{prog}",
                "-f", str(frm_cnt),
                "-i", sender_ip_list[idx],
                "-p", str(current_p),
                "-r", str(current_r),
                "-z", str(z_val)#,
                #f"> {remote_log} 2>&1"
            ]

            print(f"[INFO] Deploying {prog} to {ip}: {' '.join(cmd)}", flush=True)
#            print(f"[INFO] Deploying {prog} to {ip}")
            tag = launch_emulate(ip, cmd, prog)
            print(f"Appending {tag}")
            prog_tags.append(tag)

            current_p = current_r
            current_r = current_p + 1
            
        print("(End of emulate method)", flush=True)
        return(prog_tags)


#-----------------------------------------------------
#-----------------------------------------------------
    def send_emu(self, config="emulate.yaml", prog="zmq-event-emu-clnt"):
        """
        Send emulation command to the *first host* in the config.
        If no args passed, defaults to YAML values.
        """

        """
        if not self.emu_config or not self.emu_prog:
            print("[ERROR] Config not loaded. Run emulate() first.")
            return
        """
        
        # parse config file
        try:
            with open(config, "r") as f:
                emu_setup_prms = yaml.safe_load(f)
            print(f"[INFO] Loaded config from {config}", flush=True)
        except Exception as e:
            print(f"[ERROR] Failed to load YAML config: {e}", flush=True)
            return

        try:
            frm_sz_MB       = emu_setup_prms["frm_sz_MB"]
            frm_cnt         = emu_setup_prms["frm_cnt"]
            avg_bit_rt_Gbps = emu_setup_prms["avg_bit_rt_Gbps"]
            verbosity       = emu_setup_prms["verbosity"]
            base_port       = emu_setup_prms["base_port"]
            sender          = emu_setup_prms["sender"]
        except KeyError as e:
            print(f"[ERROR] Missing required config key: {e}", flush=True)
            return

        print(f"Sender is {sender}", flush=True)

        print(f"[INFO] Deploying {prog} to {sender}", flush=True)
 

        # Build command to send
        remote_log = f"~/{prog}.log"

        cmd = [
            f"~/{prog}",
            "-c", str(frm_cnt),
            "-s", str(frm_sz_MB),
            "-r", str(avg_bit_rt_Gbps),
            "-a", "0",
            "-p", str(base_port),
            "-v", str(verbosity),
            f"> {remote_log} 2>&1"
        ]

        print(f"[INFO] Starting {prog} on {sender}: {' '.join(cmd)}", flush=True)

        try:
            
            launch_remote(sender, cmd, prog)

            print(f"[INFO] Emulation command sent successfully to {sender}", flush=True)
        except subprocess.CalledProcessError as e:
            print(f"[ERROR] Failed to send emulation sender to {sender}: {e.stderr.decode().strip()}", flush=True)
#-----------------------------------------------------
    def parse_emu_logs(self, log_path="emu_log.txt"):
        #reset dataframes
        self.sentFrms_df        = self.sentFrms_df.iloc[0:0]
        self.drpmsdFrms_df      = self.drpmsdFrms_df.iloc[0:0]
        self.prcsdFrms_df       = self.prcsdFrms_df.iloc[0:0]
        self.drpdFrmsFrctn_df   = self.drpdFrmsFrctn_df.iloc[0:0]
        # Load and inspect
        lines = load_log_file(log_path)
        print(f"Loaded {len(lines)} lines from the log.")
        # Extract lines with frame send information for sender
        frame_rate_lines = [line for line in lines if "[emulate_stream:] Sending frame size" in line]
        min_uS = 1e30
        fs_value = pd.NA
        ts_value = pd.NA
        fn_match = pd.NA
        # Parse frame rate values from lines
        #First determine 'min_uS' value to achieve zero offset clock
        for line in frame_rate_lines:
            ts_match = re.search(r"^([\d.]+)", line)
            if ts_match:
                ts_value = float(ts_match.group(1) if ts_match else None)
                min_uS = min(ts_value, min_uS)
            else:
                print("No ts_match")

        for line in frame_rate_lines:
            fs_match = re.search(r"Sending frame size = ([\d.]+)", line)
            if fs_match:
                fs_value = B_b*float(fs_match.group(1))
            else:
                print("No fs_match")
            ts_match = re.search(r"^([\d.]+)", line)
            if ts_match:
                ts_value = float(ts_match.group(1) if ts_match else None) - min_uS
            else:
                print("No ts_match")
            fn_match = re.search(r"\(([\d.]+)\)", line)
            if fn_match:
                fn_value = float(fn_match.group(1) if fn_match else None)
            else:
                print("No fn_match")
            row = (int(0),float(ts_value),int(fn_value),float(fs_value))
            self.sentFrms_df = pd.concat([self.sentFrms_df, pd.DataFrame([row], columns=self.sentFrms_df.columns)], ignore_index=True)
        #determine emulation port range
        #port_lines = [line for line in lines if "Connecting to receiver tcp" in line]
        cmpnt_ids = []
        port_lines = [line for line in lines if "Subscribing to" in line]
        #port_lines
        for line in port_lines:
            match = re.search(r"cpu_emu ([\d.]+)", line)
            if match:
                value = float(match.group(1))
                cmpnt_ids.append(int(value))
            else:
                print("No match in port line:", line)
        self.prm_cmpnt_cnt = len(cmpnt_ids)
        print(f"parse_emu_logs: self.prm_cmpnt_cnt = {self.prm_cmpnt_cnt}")
        # Extract lines with frame send information all components
        for index, cmpnt_id in enumerate(cmpnt_ids):
            fs_value = pd.NA
            ts_value = pd.NA
            fn_value = pd.NA
            frame_send_lines = [line for line in lines if f"[cpu_emu {cmpnt_id}]:  Sending frame size" in line]    
            for line in frame_send_lines:
                fs_match = re.search(r"Sending frame size = ([\d.]+)", line)
                if fs_match:
                    fs_value = B_b*float(fs_match.group(1))
                else:
                    print("No fs_match")
                ts_match = re.search(r"^([\d.]+)", line)
                if ts_match:
                    ts_value = float(ts_match.group(1) if ts_match else None) - min_uS
                else:
                    print("No ts_match")
                fn_match = re.search(r"\(([\d.]+)\)", line)
                if fn_match:
                    fn_value = int(fn_match.group(1) if fn_match else None)
                else:
                    print("No fn_match")
                row = (int(index+1),float(ts_value),int(fn_value),float(fs_value))
                self.sentFrms_df = pd.concat([self.sentFrms_df, pd.DataFrame([row], columns=self.sentFrms_df.columns)], ignore_index=True)    
        # Extract lines with frame rcv information all components
        for index, cmpnt_id in enumerate(cmpnt_ids):
            ts_value = pd.NA
            fn_value = pd.NA
            frame_rcv_lines = [line for line in lines if f"[cpu_emu {cmpnt_id}]:  recd " in line]
            for line in frame_rcv_lines:
                ts_match = re.search(r"^([\d.]+)", line)
                if ts_match:
                    ts_value = float(ts_match.group(1) if ts_match else None) - min_uS
                else:
                    print("No ts_match")
                fn_match = re.search(r"recd ([\d.]+)", line)
                if fn_match:
                    fn_value = int(fn_match.group(1))
                else:
                    print("No fn_match")
                # New row with only some columns filled
                row = pd.DataFrame([{"component": int(index+1), "frm_nm": fn_value, "rcd_uS": ts_value}])        
                # Concatenate
                self.prcsdFrms_df = pd.concat([self.prcsdFrms_df, row], ignore_index=True)
        # Extract lines with frame done information all components
        for index, cmpnt_id in enumerate(cmpnt_ids):
            ts_value = pd.NA
            fn_value = pd.NA
            frame_don_lines = [line for line in lines if f"[cpu_emu {cmpnt_id}]:  done" in line]
            for line in frame_don_lines:
                ts_match = re.search(r"^([\d.]+)", line)
                if ts_match:
                    ts_value = float(ts_match.group(1) if ts_match else None) - min_uS
                else:
                    print("No ts_match")
                fn_match = re.search(r"done \(([\d.]+)\)", line)
                if fn_match:
                    fn_value = int(fn_match.group(1))
                else:
                    print("No fn_match")
                # New row with only some columns filled
                row = pd.DataFrame([{"component": int(index+1), "frm_nm": fn_value, "done_uS": ts_value}])        
                # update done_uS
                self.prcsdFrms_df.loc[(self.prcsdFrms_df["component"] == int(index+1)) & (self.prcsdFrms_df["frm_nm"] == fn_value), "done_uS"] = ts_value
        # Extract lines with frame size information all components
        for index, cmpnt_id in enumerate(cmpnt_ids):
            fs_value = pd.NA
            fn_match = pd.NA
            frame_sz_lines = [line for line in lines if f"[cpu_emu {cmpnt_id}]:" in line and "actual" in line]
            for line in frame_sz_lines:
                fn_match = re.search(r"\(([\d.]+)\)$", line)
                if fn_match:
                    fn_value = int(fn_match.group(1))
                else:
                    print("No fn_match")
                fs_match = re.search(r"\(actual\) ([\d.]+)", line)
                if fs_match:
                    fs_value = B_b*float(fs_match.group(1))
                else:
                    print("No fs_match")
                # update frm_sz_b
                self.prcsdFrms_df.loc[(self.prcsdFrms_df["component"] == int(index+1)) & (self.prcsdFrms_df["frm_nm"] == fn_value), "frm_sz_b"] = fs_value
                # update snt_uS by reteiving value from sentFrms_df
                if cmpnt_id != max(cmpnt_ids): #not the last or sink component
                    snt_uS = self.sentFrms_df.loc[(self.sentFrms_df["component"] == int(index+1)) & (self.sentFrms_df["frm_nm"] == fn_value), "snt_uS"].iloc[0]
                    self.prcsdFrms_df.loc[(self.prcsdFrms_df["component"] == int(index+1)) & (self.prcsdFrms_df["frm_nm"] == fn_value), "snt_uS"] = snt_uS
        # Extract lines with frame size information all components
        for index, cmpnt_id in enumerate(cmpnt_ids):
            fs_value = pd.NA
            fn_value = pd.NA
            cl_value = pd.NA
            nl_value = pd.NA
            frame_ltnc_lines = [line for line in lines if f"[cpu_emu {cmpnt_id}]:" in line and "Measured latencies" in line]
            for line in frame_ltnc_lines:
                fn_match = re.search(r"\(([\d.]+)\)$", line)
                if fn_match:
                    fn_value = int(fn_match.group(1))
                else:
                    print("No fn_match")
                cl_match = re.search(r"last_cmp_lat_uS = ([\d.]+)", line)
                if cl_match:
                    cl_value = float(cl_match.group(1))
                else:
                    print("No cl_match")
                nl_match = re.search(r"last_nw_lat_uS = ([\d.]+)", line)
                if nl_match:
                    nl_value = float(nl_match.group(1))
                else:
                    print("No cl_match")
                # update done_uS
                self.prcsdFrms_df.loc[(self.prcsdFrms_df["component"] == int(index+1)) & (self.prcsdFrms_df["frm_nm"] == fn_value), "cmp_ltnc_uS"] = cl_value
                self.prcsdFrms_df.loc[(self.prcsdFrms_df["component"] == int(index+1)) & (self.prcsdFrms_df["frm_nm"] == fn_value), "ntwrk_lt_uS"] = nl_value
        # Extract lines with frame rcv information all components
        for index, cmpnt_id in enumerate(cmpnt_ids):
            fs_value = pd.NA
            ts_value = pd.NA
            fn_value = pd.NA
            frame_drp_lines = [line for line in lines if f"[cpu_emu {cmpnt_id}]" in line and "dropped" in line]
            for line in frame_drp_lines:
                ts_match = re.search(r"^([\d.]+)", line)
                if ts_match:
                    ts_value = float(ts_match.group(1) if ts_match else None) - min_uS
                else:
                    print("No ts_match")
                fn_match = re.search(r"\(([\d.]+)\)", line)
                if fn_match:
                    fn_value = int(fn_match.group(1))
                else:
                    print("No fn_match")
                # New row with only some columns filled
                row = pd.DataFrame([{"component": int(index+1), "frm_nm": fn_value, "rcd_uS": ts_value}])        
                # Concatenate
                self.drpmsdFrms_df = pd.concat([self.drpmsdFrms_df, row], ignore_index=True)

        self.cnst_all_frm_set = set(range(1, self.sentFrms_df.loc[self.sentFrms_df["component"] == 0, "frm_nm"].max() + 1))   # range is exclusive at the end, so add 1 for inclusive
                                            
#-----------------------------------------------------
    def plot_send_bit_rate(self):
        """
        Plotting procedure

        Parameters
        ----------
        tag : string to prepend to plots for disambuguation

        Returns
        -------
        none
        """
        for i in range(0, self.prm_cmpnt_cnt): #last component does not send

            sim_tm_uS = self.sentFrms_df.loc[self.sentFrms_df["component"] == i, "snt_uS"][1:].reset_index(drop=True)
            dts_S = u_1*(self.sentFrms_df.loc[self.sentFrms_df["component"] == i, "snt_uS"][1:].reset_index(drop=True) - self.sentFrms_df.loc[self.sentFrms_df["component"] == i, "snt_uS"][:-1].reset_index(drop=True))
            szs_b = self.sentFrms_df.loc[self.sentFrms_df["component"] == i, "frm_sz_b"][1:].reset_index(drop=True)

            btRt_Mbps = one_M*szs_b/dts_S
            # Display statistics
            btRt_stats_Mbps = compute_moments(btRt_Mbps)

            print(f"Component {i} Send bit Rate Statistics (Mbps):", file=self.log_file)
            for k, v in btRt_stats_Mbps.items():
                print(f"{k}: {v:.3f}", file=self.log_file)

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
            plt.savefig(f"Cmpnt_{i}_SndBtRt.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
#-----------------------------------------------------
    def plot_rcv_frm_rate(self):
        """
        Plotting procedure

        Parameters
        ----------
        none

        Returns
        -------
        none
        """
        for i in range(1, self.prm_cmpnt_cnt + 1): ######################################################################## 1 + 1): #
            dt_S_arr           = np.diff(u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"])
            frame_rates_Hz_arr = np.array(1/dt_S_arr)

            # Display statistics
            frame_rate_stats = compute_moments(frame_rates_Hz_arr)

            print(f"Component {i} Recv Frame Rate: (Hz)", file=self.log_file)
            for k, v in frame_rate_stats.items():
                print(f"{k}: {v:.3f}", file=self.log_file)

            # Plot Frame Rate

            plt.figure(figsize=(8, 5))

            timestamps_S_arr = np.array(u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"][1:])
            window = 1
            sns.lineplot(x=timestamps_S_arr[window-1:]/60.0, y=moving_average(frame_rates_Hz_arr, n=window), marker="o")
            # plt.plot(timestamps_S_arr/60.0, frame_rates_Hz_arr, marker='o', linestyle='-')
            # plt.ylim(frame_rates_Hz_arr.min(), max(frame_rates_Hz_arr))
            plt.ticklabel_format(style='plain', axis='y')   # disable scientific/offset notation

            plt.title(f"Component {i} Recv Frame Rate (Hz)")
            plt.xlabel("Minutes")
            plt.ylabel("Hz")
            plt.tight_layout()
            plt.savefig(f"Cmpnt_{i}_RcvFrmRt.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
#-----------------------------------------------------
    def plot_rcv_frm_dlta(self):
        """
        Plotting procedure

        Parameters
        ----------
        none

        Returns
        -------
        none
        """

        for i in range(1, self.prm_cmpnt_cnt + 1):

            plt.figure(figsize=(8, 5))

            rcd_uS =  self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i,  "rcd_uS"][1:].reset_index(drop=True)
            done_uS = self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "done_uS"][:-1].reset_index(drop=True)

            delta_mS = u_m*(rcd_uS - done_uS)

            # Display statistics
            delta_stats_mS = compute_moments(delta_mS)

            print(f"Component {i} Recv Frame Delta Statistics (mS):", file=self.log_file)
            for k, v in delta_stats_mS.items():
                print(f"{k}: {v:.3f}", file=self.log_file)

            plt.figure(figsize=(8, 5))
            sns.lineplot(x=self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "frm_nm"][1:].reset_index(drop=True), y=delta_mS , marker="o")       # line + markers
            plt.title(f"Component {i} Recv Frame Delta")
        #    plt.xlabel("Minutes")
            plt.xlabel("Frame")
            plt.ylabel("mS")
            plt.tight_layout()
            plt.savefig(f"Cmpnt_{i}_RcvFrmRtDlt.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def plot_rcv_bit_rate(self):
        """
        Plotting procedure

        Parameters
        ----------
        none

        Returns
        -------
        none
        """

        for i in range(1, self.prm_cmpnt_cnt + 1):

            sim_tm_uS = self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"][1:].reset_index(drop=True)
            dts_S = u_1*(self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"][1:].reset_index(drop=True) - self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"][:-1].reset_index(drop=True))
            szs_b = self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "frm_sz_b"][1:].reset_index(drop=True)

            btRt_Mbps = one_M*szs_b/dts_S
            # Display statistics
            btRt_stats_Mbps = compute_moments(btRt_Mbps)

            print(f"Component {i} Recv bit Rate Statistics (Mbps):", file=self.log_file)
            for k, v in btRt_stats_Mbps.items():
                print(f"{k}: {v:.3f}", file=self.log_file)

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
            plt.savefig(f"Cmpnt_{i}_RcvBtRt.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def plot_cmp_ltnc(self):
        """
        Plotting procedure

        Parameters
        ----------
        none

        Returns
        -------
        none
        """

        for i in range(1, self.prm_cmpnt_cnt + 1):

            cmpLt_mS = u_m*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "cmp_ltnc_uS"]
            # Display statistics
            cmpLt_stats_mS = compute_moments(cmpLt_mS)

            print(f"Component {i} Comp Latency Statistics (mS):", file=self.log_file)
            for k, v in cmpLt_stats_mS.items():
                print(f"{k}: {v:.3f}", file=self.log_file)

            # Plot
            plt.figure(figsize=(10, 4))
            # plt.plot(u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"]/60, cmpLt_mS, marker='o', linestyle='-')
            sns.lineplot(x=u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"]/60, y=cmpLt_mS, marker='o', linestyle='-')
            plt.title(f"Component {i} Comp Latency (mS)")
            plt.xlabel('Time (minutes)')
            plt.ylabel('Latency (mS)')
            #plt.ylim(0, np.isfinite(recd['fps_roll'].mean()))
            plt.grid(True)
            plt.tight_layout()
            plt.savefig(f"Cmpnt_{i}_CmpLtnc.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def plot_ntwrk_ltnc(self):
        """
        Plotting procedure

        Parameters
        ----------
        none

        Returns
        -------
        none
        """

        for i in range(1, self.prm_cmpnt_cnt + 1):

            ntwrkLt_uS = self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "ntwrk_lt_uS"]
            # Display statistics
            ntwrkLt_stats_uS = compute_moments(ntwrkLt_uS)

            print(f"Component {i} Network Latency Statistics (uS):", file=self.log_file)
            for k, v in ntwrkLt_stats_uS.items():
                print(f"{k}: {v:.3f}", file=self.log_file)

            # Plot
            plt.figure(figsize=(10, 4))
            # plt.plot(u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"]/60, ntwrkLt_uS, marker='o', linestyle='-')
            sns.lineplot(x=u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"]/60, y=ntwrkLt_uS, marker='o', linestyle='-')
            plt.title(f"Component {i} Network Latency (uS)")
            plt.xlabel('Time (minutes)')
            plt.ylabel('Latency (uS)')
            #plt.ylim(0, np.isfinite(recd['fps_roll'].mean()))
            plt.grid(True)
            plt.tight_layout()
            plt.savefig(f"Cmpnt_{i}_NtwrkLtnc.png", dpi=300, bbox_inches="tight")  #plt.show()

#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def plot_frm_rcv(self):
        """
        Plotting procedure

        Parameters
        ----------
        none

        Returns
        -------
        none
        """

        for i in range(1, self.prm_cmpnt_cnt + 1):

            # Plot
            plt.figure(figsize=(10, 4))
            plt.plot(u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"]/60, self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "frm_nm"], marker='o', linestyle='-')
            # sns.lineplot(x=u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"]/60, y=self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "frm_nm"], marker='o')
            plt.title(f"Component {i} Frame Recption Over Time")
            plt.xlabel('Time (Minutes)')
            plt.ylabel('recd (Frame num)')
            plt.ylim(0, max(self.prcsdFrms_df['frm_nm']) * 1.2)
            plt.grid(True)
            plt.tight_layout()
            plt.savefig(f"Cmpnt_{i}_FrmRcvTm.png", dpi=300, bbox_inches="tight")  #plt.show()
#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def plot_tm_frm_rcv(self):
        for i in range(1, self.prm_cmpnt_cnt + 1):
            # Plot
            plt.plot(u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"]/60, self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "frm_nm"], marker='o', linestyle='-')

        # Add labels and legend
        plt.xlabel('Sim Time (Mins)')
        plt.ylabel('Frame Number')
        plt.title('Time Frame Recvd')

        # Show grid and plot
        plt.grid(True)
        plt.tight_layout()
        plt.savefig("TmFrmRcvd.png", dpi=300, bbox_inches="tight")  #plt.show()
#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def plot_tm_frm_dn(self):
        """
        Plotting procedure

        Parameters
        ----------
        none

        Returns
        -------
        none
        """

        for i in range(1, self.prm_cmpnt_cnt + 1):
            # Plot
            plt.plot(u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "done_uS"]/60, self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "frm_nm"], marker='o', linestyle='-')

        # Add labels and legend
        plt.xlabel('Sim Time (Hrs)')
        plt.ylabel('Frame Number')
        plt.title('Time Frame Done')

        # Show grid and plot
        plt.grid(True)
        plt.tight_layout()
        plt.savefig("TmFrmRDn.png", dpi=300, bbox_inches="tight")  #plt.show()
#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def calc_drp_sets_by_component(self):
        """
        Lists droped frames for each component

        Parameters
        ----------
        none

        Returns
        -------
            none
        """
        self.drp_sets_by_component = self.drpmsdFrms_df.groupby("component")["frm_nm"].apply(set)
        cmpnt_drp_nms = set(self.drpmsdFrms_df["component"].unique())
        for c in cmpnt_drp_nms:
            print(f"Number of drops for component {c}: {len(self.drp_sets_by_component[c])}", file=self.log_file)
#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def calc_prcsd_frm_sets_by_component(self):
        """
        Lists processed frames for each component

        Parameters
        ----------
        none

        Returns
        -------
            none
        """
        self.prcsd_frm_sets_by_component = self.prcsdFrms_df.groupby("component")["frm_nm"].apply(set)
        cmpnt_frn_nms = set(self.prcsdFrms_df["component"].unique())
        for c in cmpnt_frn_nms:
            print(f"Number of processed frames for component {c}: {len(self.prcsd_frm_sets_by_component[c])}", file=self.log_file)
#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def plot_drps_mss(self):
        """
        Plotting procedure

        Parameters
        ----------
        none

        Returns
        -------
        none
        """

        self.calc_drp_sets_by_component()
        self.calc_prcsd_frm_sets_by_component()
        cmpnt_drp_nms = set(self.drpmsdFrms_df["component"].unique())
        #dataframe record for components with no drops
        for c in set(range(1,self.prm_cmpnt_cnt+1)) - cmpnt_drp_nms: #the set of compnents with no drops
            row = (c,0)
            self.drpdFrmsFrctn_df = pd.concat([self.drpdFrmsFrctn_df, pd.DataFrame([row], columns=self.drpdFrmsFrctn_df.columns)], ignore_index=True)

        #dataframe record for components with drops
        self.drp_sets_by_component = self.drpmsdFrms_df.groupby("component")["frm_nm"].apply(set)
        for c in cmpnt_drp_nms:
            row = (c,len(self.drp_sets_by_component[c])/(len(self.drp_sets_by_component[c]) + len(self.prcsd_frm_sets_by_component[c])))
            self.drpdFrmsFrctn_df = pd.concat([self.drpdFrmsFrctn_df, pd.DataFrame([row], columns=self.drpdFrmsFrctn_df.columns)], ignore_index=True)


        # self.drpdFrmsFrctn_df

        # Plot
        from matplotlib.ticker import MaxNLocator

        x = self.drpdFrmsFrctn_df['component'].astype(int)
        y = 100*self.drpdFrmsFrctn_df['drp_frctn']
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
        plt.savefig("CmpntDrps.png", dpi=300, bbox_inches="tight")  #plt.show()

        #Frames that components missed because they were never received
        msdFrmsFrctn_df = pd.DataFrame({
            "component": pd.Series(dtype=int),
            "msd_frctn": pd.Series(dtype=int)
        })

        #Compnents miss frames when upstream senders do not send
        #Upstrean components do not send frames they miss or drop
        #The effect is cummulative for downstream components

        #For each component, This equates to the number of sender frames - (number processd + the number droped (since they were not missed))

        for c in range(1,self.prm_cmpnt_cnt+1):
            row = (c,1-(len(set(self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == c, "frm_nm"])) + len(set(self.drpmsdFrms_df.loc[self.drpmsdFrms_df["component"] == c, "frm_nm"])))/len(self.cnst_all_frm_set))
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
        plt.savefig("CmpntMss.png", dpi=300, bbox_inches="tight")  #plt.show()
#-----------------------------------------------------
#-----------------------------------------------------
#-----------------------------------------------------
    def plot_all(self):
        """
        Plotting procedure

        Parameters
        ----------
        none

        Returns
        -------
        none
        """

        self.plot_send_bit_rate()
        self.plot_rcv_frm_rate()
        self.plot_rcv_frm_dlta()
        self.plot_rcv_bit_rate()
        self.plot_cmp_ltnc()
        self.plot_ntwrk_ltnc()
        self.plot_frm_rcv()
        self.plot_tm_frm_rcv()
        self.plot_tm_frm_dn()
        self.plot_drps_mss()

#-----------------------------------------------------
#-----------------------------------------------------
# Run script
if __name__ == "__main__":
    #seed = int.from_bytes(os.urandom(8), "big")
    processor = RTDP(rng_seed = None, directory=".", extension=".txt", log_file="logfile.txt", sim_config="cpu_sim.yaml")
    processor.sim()
    processor.plot_all()

#-----------------------------------------------------
#-----------------------------------------------------

#pip3 install pandas matplotlib  seaborn scipy
##python3 -m pip install --upgrade numpy

#$ python
#Python 3.6.8 (default, Nov 15 2024, 08:11:39)
#[GCC 8.5.0 20210514 (Red Hat 8.5.0-22)] on linux
#Type "help", "copyright", "credits" or "license" for more information.
#>>> from rtdp import RTDP
#>>> rtdp = RTDP(rng_seed=7, log_file="z.txt")
#>>> rtdp.sim()
#>>> rtdp.emulate(config="emulate.yaml", prog="cpu_emu")   # deploys daisy chain
#>>> rtdp send_emu(self, config="emulate.yaml", prog="zmq-event-emu-clnt")

#>>> rtdp.plot_all()
#$ for f in *.png; do eog "$f" & done
#$ less z.txt
#$ killall eog
#$ rm *png *txt


# for numpy:
#plt.plot(
#    (u_1 * sim_tm_uS.values) / 60,
#    btRt_Mbps.values,
#    marker='o',
#    linestyle='-'
#)
# or
 
# For some numpy issues 
# x = (u_1 * sim_tm_uS.to_numpy()) / 60
# y = btRt_Mbps.to_numpy()
# plt.plot(x, y, marker='o', linestyle='-')

