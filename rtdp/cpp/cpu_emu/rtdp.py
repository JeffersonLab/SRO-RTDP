#!/usr/bin/env python3
import os
import math

import pandas as pd
from pandas import json_normalize
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy.stats import skew, kurtosis
import re
from datetime import datetime
import yaml
import subprocess

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

class RTDP:
    
    def __del__(self):
        self.log_file.close()   
        
    def __init__(self, rng_seed = None, directory=".", extension=".txt", log_file="logfile.txt", sim_config="cpu_sim.yaml"):
    
        self.rng       = np.random.default_rng(rng_seed)
        self.directory = directory
        self.extension = extension
        self.log_file  = log_file
        self.sim_config= sim_config
        self.log_file = open(self.log_file, "w")

        #Frames actually processed by components
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

        #Frames that components were not ready to receive
        self.drpmsdFrms_df = pd.DataFrame({
            "component":   pd.Series(dtype=int),
            "rcd_uS":      pd.Series(dtype=float),
            "frm_nm":      pd.Series(dtype=int),
            "frm_sz_b":    pd.Series(dtype=float),
            "lstDone_uS":  pd.Series(dtype=float)
        }) # contains no dropped/missed frames

        #Data on all sent frames - component 0 is source
        self.sentFrms_df = pd.DataFrame({
            "component":   pd.Series(dtype=int),
            "snt_uS":      pd.Series(dtype=float),
            "frm_nm":      pd.Series(dtype=int),
            "frm_sz_b":    pd.Series(dtype=float)
        }) 
        self.drpdFrmsFrctn_df = pd.DataFrame({
            "component": pd.Series(dtype=int),
            "drp_frctn":   pd.Series(dtype=int)
        })

        try:
            with open(self.sim_config, "r") as f:
                data = yaml.safe_load(f)
            print(f"[INFO] Loaded config from {self.sim_config}")
        except Exception as e:
            print(f"[ERROR] Failed to load YAML config: {e}")
            return

        # Flatten into a DataFrame
        prmtrs_df = json_normalize(data, sep=".")
        print(prmtrs_df.T, file=self.log_file)  # transpose to make it easier to read

        self.prm_cmp_ltnc_nS_B   = float(prmtrs_df['cmp_ltnc_nS_B'].iloc[0])
        self.prm_output_size_GB  = float(prmtrs_df['output_size_GB'].iloc[0])
        self.prm_nic_Gbps        = float(prmtrs_df['nic_Gbps'].iloc[0])
        self.prm_daq_frm_sz_MB   = float(prmtrs_df['frame_sz_MB'].iloc[0])
        self.prm_frame_cnt       =   int(prmtrs_df['frame_cnt'].iloc[0])
        self.prm_cmpnt_cnt       =   int(prmtrs_df['cmpnt_cnt'].iloc[0])
        self.prm_avg_bit_rt_Gbps = float(prmtrs_df['avg_bit_rt_Gbps'].iloc[0])
        #-----------------------------------------------------
        # Network Latency Samples

        # Target mean and std
        ntwrk_lt_mean_uS = float(one_u*M_1*self.prm_daq_frm_sz_MB/(b_B*G_1*self.prm_nic_Gbps))
        ntwrk_lt_sd_uS = ntwrk_lt_mean_uS/3

        # Bound samples to lower bound
        cnst_nl_smpls_uS = self.gen_gamma_samples(ntwrk_lt_mean_uS, ntwrk_lt_sd_uS, int(1e4))

        # Verification
        nl_stats_uS = self.compute_moments(cnst_nl_smpls_uS)

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
        ntwrk_lt_mean_uS = float(one_u*M_1*self.prm_daq_frm_sz_MB/(b_B*G_1*self.prm_nic_Gbps))
        ntwrk_lt_sd_uS = ntwrk_lt_mean_uS/3

        # Sample from Gamma
        raw_samples = self.gen_gamma_samples(ntwrk_lt_mean_uS, ntwrk_lt_sd_uS, int(1e4))
        # Bound samples to lower bound
        cnst_nl_smpls_uS = raw_samples[raw_samples >= ntwrk_lt_mean_uS]

        # Verification

        nl_stats_uS = self.compute_moments(cnst_nl_smpls_uS)
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

        self.cnst_daq_fs_mean_B = float(M_1*self.prm_daq_frm_sz_MB)
        # Target mean and std
        cnst_fs_std_B = 0.1*self.cnst_daq_fs_mean_B

        # Sample from Gamma
        cnst_fs_smpls_B = self.gen_gamma_samples(self.cnst_daq_fs_mean_B, cnst_fs_std_B, int(1e4))

        # Verification
        cnst_fs_stats_B = self.compute_moments(cnst_fs_smpls_B)
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

        cnst_os_mean_B = float(G_1*self.prm_output_size_GB)
        # Target mean and std
        cnst_os_std_B = 0.1*cnst_os_mean_B

        # Sample from Gamma
        self.os_smpls_B = self.gen_gamma_samples(cnst_os_mean_B, cnst_os_std_B, int(1e4))

        # Verification
        os_stats_B = self.compute_moments(self.os_smpls_B)
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

    # Compute statistical moments: mean, std, skewness, kurtosis
    def compute_moments(self, series):
        values = series #.dropna()
        return {
            'count': len(values),
            'mean': values.mean(),
            'std': values.std(),
            'skew': skew(values),
            'kurtosis': kurtosis(values)
        }
    #-----------------------------------------------------
    def moving_average(self, a, n=5):
        ret = np.cumsum(a, dtype=float)
        ret[n:] = ret[n:] - ret[:-n]
        return ret[n-1:] / n
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

    def sim(self):

        #set of all frame numbers from sender
        self.cnst_all_frm_set = set(range(1, self.prm_frame_cnt + 1))   # range is exclusive at the end, so add 1 for inclusive

        # component 0 is the sender
        clk_uS      = zeros = np.zeros(self.prm_cmpnt_cnt+1, dtype=float) #Time last frame finished processing

        vrbs = True

        cnst_swtch_lt_uS = 1 #switch latency

        #Simulation
        for f in range(0, self.prm_frame_cnt):
            # impulse
        #    if f==self.prm_frame_cnt/2: self.prm_nic_Gbps /= 2 #######################################
        #    if f==self.prm_frame_cnt/2: self.prm_cmp_ltnc_nS_B *= 2 #######################################
            cnst_daq_frm_sz0_b = B_b*self.cnst_daq_fs_mean_B; #cnst_fs_smpls_B[f]
            if vrbs: print(f"{clk_uS[0]} Send frame {f} Size (b): {cnst_daq_frm_sz0_b:10.2f}", file=self.log_file)
            #component zero is the sender
            row = (0,clk_uS[0],f,cnst_daq_frm_sz0_b)
            self.sentFrms_df = pd.concat([self.sentFrms_df, pd.DataFrame([row], columns=self.sentFrms_df.columns)], ignore_index=True)
            for c in range(1, self.prm_cmpnt_cnt+1):
                #set component forwarding frame size to component output Size
                frm_szc_b = B_b*self.os_smpls_B[f*self.prm_cmpnt_cnt+c]
                clk_c = clk_uS[c-1] #temp clk base = upstream senders 'done/sent' value
                # set recvd frame size: cmpnt #1 is senders size, all others are cmpnt output size
                # it is assumed that the sender represents a DAQ with fixed frame size
                # inducing (highly)? variable computational lateny
                if c == 1:
                    frm_sz_b = cnst_daq_frm_sz0_b
                else:
                    frm_sz_b = B_b*self.os_smpls_B[f*self.prm_cmpnt_cnt+c-1]

                # component receives with network latency offset from upstream sender time
                ntwrk_lt_mean_uS = float(one_u*frm_sz_b/(G_1*self.prm_nic_Gbps))
                ntwrk_lt_sd_uS = math.ceil(ntwrk_lt_mean_uS/20) #5%
                ntwrk_lt_uS = 0
                while ntwrk_lt_uS < ntwrk_lt_mean_uS: #enforce lower bound
                    ntwrk_lt_uS = self.gen_gamma_samples(ntwrk_lt_mean_uS, ntwrk_lt_sd_uS, int(1))[0]

                ntwrk_lt_uS += cnst_swtch_lt_uS  #add switch latency
                clk_c += ntwrk_lt_uS  #Update temp clk for net latency
                rcd_uS = clk_c #Time would recv from upstream sender if ready
                if vrbs: print(f"{clk_c} Component {c} Recv Frame {f} Size (b): {frm_sz_b:10.2f}", file=self.log_file)
                if (clk_uS[c] > clk_c): # True -> not ready to recv
                    if vrbs: print(f"{clk_c} Component {c} Missed Frame {f}", file=self.log_file)
                    row = (c,rcd_uS,f,frm_sz_b,clk_uS[c])
                    self.drpmsdFrms_df = pd.concat([self.drpmsdFrms_df, pd.DataFrame([row], columns=self.drpmsdFrms_df.columns)], ignore_index=True)
                    break; #If temp clk > components last 'done' time, frame is missed for this and all downstream components
                # component processes with compute latency
                cmp_ltnc_nS_B = self.gen_gamma_samples(self.prm_cmp_ltnc_nS_B, self.prm_cmp_ltnc_nS_B/10, int(1))[0]
                cmp_ltnc_uS = float(n_u*cmp_ltnc_nS_B*frm_sz_b*b_B)
                clk_c += cmp_ltnc_uS #Update temp clk for compute latency
                clk_c += 10 #add overhead
                snt_uS = clk_c
                row = (c,snt_uS,f,frm_szc_b)
                self.sentFrms_df = pd.concat([self.sentFrms_df, pd.DataFrame([row], columns=self.sentFrms_df.columns)], ignore_index=True)
                clk_c += 10 #add overhead
                clk_uS[c]  = clk_c #Set as last 'done' time
                if vrbs and c == self.prm_cmpnt_cnt: print(f"Update sim clock to {clk_c} (uS) for component {c}", file=self.log_file)
                if vrbs: print(f"{clk_c} Component {c} Done Frame {f} Size (b): {frm_sz_b:10.2f}", file=self.log_file)
                #add self.prcsdFrms_df row
                row = (c,rcd_uS,f,frm_sz_b,cmp_ltnc_uS,ntwrk_lt_uS,snt_uS,clk_uS[c])
                self.prcsdFrms_df = pd.concat([self.prcsdFrms_df, pd.DataFrame([row], columns=self.prcsdFrms_df.columns)], ignore_index=True)
            # Sender Rate Sleep
            rtSlp_uS   = float(one_u*cnst_daq_frm_sz0_b / (G_1*self.prm_avg_bit_rt_Gbps))
            clk_uS[0] += rtSlp_uS

#-----------------------------------------------------

    def emulate(self, file="cpu_emu.yaml", prog="xyz"):
        """
        Load YAML config and deploy daisy-chained binaries across remote hosts.
        """
        try:
            with open(file, "r") as f:
                self.emu_config = yaml.safe_load(f)
            self.emu_prog = prog
            print(f"[INFO] Loaded config from {file}")
        except Exception as e:
            print(f"[ERROR] Failed to load YAML config: {e}")
            return

        try:
            cmpnt_cnt = self.emu_config["cmpnt_cnt"]
            base_port  = self.emu_config["base_port"]
            frm_cnt    = self.emu_config["frm_cnt"]
            frm_sz_MB     = self.emu_config["frm_sz_MB"]
            avg_bit_rt_Gbps     = self.emu_config["avg_bit_rt_Gbps"]
            ip_list    = self.emu_config["hosts"]
        except KeyError as e:
            print(f"[ERROR] Missing required config key: {e}")
            return

        # Flatten into a DataFrame
        prmtrs_df = json_normalize(self.emu_config, sep=".")
        print(prmtrs_df.T, file=self.log_file)  # transpose to make it easier to read

        prm_cmp_ltnc_nS_B   = float(prmtrs_df['cmp_ltnc_nS_B'].iloc[0])
        prm_output_size_GB  = float(prmtrs_df['output_size_GB'].iloc[0])
        prm_daq_frm_sz_MB   = float(prmtrs_df['frm_sz_MB'].iloc[0])
        prm_frm_cnt       =   int(prmtrs_df['frm_cnt'].iloc[0])
        prm_cmpnt_cnt       =   int(prmtrs_df['cmpnt_cnt'].iloc[0])
        prm_avg_bit_rt_Gbps = float(prmtrs_df['avg_bit_rt_Gbps'].iloc[0])

        prm_mem_ftprint_GB = float(prmtrs_df['mem_ftprint_GB'].iloc[0])
        prm_sleep          = bool(prmtrs_df['sleep'].iloc[0])
        prm_thread_cnt     = int(prmtrs_df['thread_cnt'].iloc[0])
        prm_verbosity      = int(prmtrs_df['verbosity'].iloc[0])
        prm_base_port      = int(prmtrs_df['base_port'].iloc[0])

        ip_list = self.emu_config.get("hosts", [])

        current_p = prm_base_port
        current_r = prm_base_port + 1

        for idx, ip in enumerate(ip_list):
            z_val = 1 if idx == len(ip_list) - 1 else 0

            cmd = [
                f"./{prog}",
                "-b", "1",            # placeholder latency per GB
                "-f", str(frm_cnt),
                "-i", ip,
                "-m", "1",            # memory footprint GB
                "-o", "1",            # output size GB
                "-p", str(current_p),
                "-r", str(current_r),
                "-s", "0",
                "-t", "10",
                "-v", "0",
                "-y", file,
                "-z", str(z_val)
            ]

            print(f"[INFO] Deploying to {ip}: {' '.join(cmd)}")

            try:
                subprocess.run(
                    ["scp", prog, f"{ip}:/tmp/{prog}"],
                    check=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE
                )
                print(f"[INFO] Copied {prog} to {ip}:/tmp/{prog}")
            except subprocess.CalledProcessError as e:
                print(f"[ERROR] Failed to copy binary to {ip}: {e.stderr.decode().strip()}")
                continue

            try:
                ssh_cmd = ["ssh", ip, f"chmod +x /tmp/{prog} && /tmp/{prog} {' '.join(cmd[1:])}"]
                subprocess.run(
                    ssh_cmd,
                    check=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE
                )
                print(f"[INFO] Successfully started {prog} on {ip}")
            except subprocess.CalledProcessError as e:
                print(f"[ERROR] Failed to run {prog} on {ip}: {e.stderr.decode().strip()}")
                continue

            current_p = current_r
            current_r = current_p + 1

    def send_emu(self, frm_sz_MB=None, frm_cnt=None, bit_rt=None):
        """
        Send emulation command to the *first host* in the config.
        If no args passed, defaults to YAML values.
        """
        if not self.emu_config or not self.emu_prog:
            print("[ERROR] Config not loaded. Run emulate() first.")
            return

        frm_sz_MB  = frm_sz_MB  if frm_sz_MB  is not None else self.emu_config.get("frm_sz_MB")
        frm_cnt = frm_cnt if frm_cnt is not None else self.emu_config.get("frm_cnt")
        avg_bit_rt_Gbps  = avg_bit_rt_Gbps  if avg_bit_rt_Gbps  is not None else self.emu_config.get("avg_bit_rt_Gbps")

        if not ip_list:
            print("[ERROR] No hosts defined in config.")
            return

        first_ip = ip_list[0]

        # Build command to send
        cmd = [
            f"/tmp/{self.emu_prog}",
            "-f", str(frm_cnt),
            "-o", str(frm_sz_MB),
            "-r", str(avg_bit_rt_Gbps),
            "-s", "0",
            "-v", "1"
        ]

        print(f"[INFO] Sending emulation from {first_ip}: {' '.join(cmd)}")

        try:
            ssh_cmd = ["ssh", first_ip, " ".join(cmd)]
            subprocess.run(
                ssh_cmd,
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE
            )
            print(f"[INFO] Emulation command sent successfully to {first_ip}")
        except subprocess.CalledProcessError as e:
            print(f"[ERROR] Failed to send emulation to {first_ip}: {e.stderr.decode().strip()}")


#-----------------------------------------------------

    def plot_send_bit_rate(self):
        for i in range(0, self.prm_cmpnt_cnt): #last component does not send

            sim_tm_uS = self.sentFrms_df.loc[self.sentFrms_df["component"] == i, "snt_uS"][1:].reset_index(drop=True)
            dts_S = u_1*(self.sentFrms_df.loc[self.sentFrms_df["component"] == i, "snt_uS"][1:].reset_index(drop=True) - self.sentFrms_df.loc[self.sentFrms_df["component"] == i, "snt_uS"][:-1].reset_index(drop=True))
            szs_b = self.sentFrms_df.loc[self.sentFrms_df["component"] == i, "frm_sz_b"][1:].reset_index(drop=True)

            btRt_Mbps = one_M*szs_b/dts_S
            # Display statistics
            btRt_stats_Mbps = self.compute_moments(btRt_Mbps)

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
    def plot_rcv_frm_rate(self):
        for i in range(1, self.prm_cmpnt_cnt + 1): ######################################################################## 1 + 1): # 
            dt_S_arr           = np.diff(u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"])
            frame_rates_Hz_arr = np.array(1/dt_S_arr)

            # Display statistics
            frame_rate_stats = self.compute_moments(frame_rates_Hz_arr)

            print(f"Component {i} Recv Frame Rate: (Hz)", file=self.log_file)
            for k, v in frame_rate_stats.items():
                print(f"{k}: {v:.3f}", file=self.log_file)

            # Plot Frame Rate

            plt.figure(figsize=(8, 5))

            timestamps_S_arr = np.array(u_1*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"][1:])
            window = 1
            sns.lineplot(x=timestamps_S_arr[window-1:]/60.0, y=self.moving_average(frame_rates_Hz_arr, n=window), marker="o")
            # plt.plot(timestamps_S_arr/60.0, frame_rates_Hz_arr, marker='o', linestyle='-')
            # plt.ylim(frame_rates_Hz_arr.min(), max(frame_rates_Hz_arr))
            plt.ticklabel_format(style='plain', axis='y')   # disable scientific/offset notation

            plt.title(f"Component {i} Recv Frame Rate (Hz)")
            plt.xlabel("Minutes")
            plt.ylabel("Hz")
            plt.tight_layout()
            plt.savefig(f"Cmpnt_{i}_RcvFrmRt.png", dpi=300, bbox_inches="tight")  #plt.show()

    def plot_rcv_frm_dlta(self):
        for i in range(1, self.prm_cmpnt_cnt + 1):

            plt.figure(figsize=(8, 5))

            rcd_uS =  self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i,  "rcd_uS"][1:].reset_index(drop=True)
            done_uS = self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "done_uS"][:-1].reset_index(drop=True)

            delta_mS = u_m*(rcd_uS - done_uS)

            # Display statistics
            delta_stats_mS = self.compute_moments(delta_mS)

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
    def plot_rcv_recv_bit_rate(self):
        for i in range(1, self.prm_cmpnt_cnt + 1):

            sim_tm_uS = self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"][1:].reset_index(drop=True)
            dts_S = u_1*(self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"][1:].reset_index(drop=True) - self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "rcd_uS"][:-1].reset_index(drop=True))
            szs_b = self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "frm_sz_b"][1:].reset_index(drop=True)

            btRt_Mbps = one_M*szs_b/dts_S
            # Display statistics
            btRt_stats_Mbps = self.compute_moments(btRt_Mbps)

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
    def plot_cmp_ltnc(self):
        for i in range(1, self.prm_cmpnt_cnt + 1):

            cmpLt_mS = u_m*self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "cmp_ltnc_uS"]
            # Display statistics
            cmpLt_stats_mS = self.compute_moments(cmpLt_mS)

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
    def plot_ntwrk_ltnc(self):
        for i in range(1, self.prm_cmpnt_cnt + 1):

            ntwrkLt_uS = self.prcsdFrms_df.loc[self.prcsdFrms_df["component"] == i, "ntwrk_lt_uS"]
            # Display statistics
            ntwrkLt_stats_uS = self.compute_moments(ntwrkLt_uS)

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
    def plot_frm_rcv(self):
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
    def plot_tm_frm_dn(self):
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
    def calc_drp_sets_by_component(self):
        self.drp_sets_by_component = self.drpmsdFrms_df.groupby("component")["frm_nm"].apply(set)
        cmpnt_drp_nms = set(self.drpmsdFrms_df["component"].unique())
        for c in cmpnt_drp_nms:
            print(f"Number of drops for component {c}: {len(self.drp_sets_by_component[c])}", file=self.log_file)
#-----------------------------------------------------
    def calc_prcsd_frm_sets_by_component(self):
        self.prcsd_frm_sets_by_component = self.prcsdFrms_df.groupby("component")["frm_nm"].apply(set)
        cmpnt_frn_nms = set(self.prcsdFrms_df["component"].unique())
        for c in cmpnt_frn_nms:
            print(f"Number of processed frames for component {c}: {len(self.prcsd_frm_sets_by_component[c])}", file=self.log_file)
#-----------------------------------------------------
    def plot_drps_mss(self):
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
    def plot_all(self):
        self.plot_send_bit_rate()
        self.plot_rcv_frm_rate()
        self.plot_rcv_frm_dlta()
        self.plot_rcv_recv_bit_rate()
        self.plot_cmp_ltnc()
        self.plot_ntwrk_ltnc()
        self.plot_frm_rcv()
        self.plot_tm_frm_rcv()
        self.plot_tm_frm_dn()
        self.plot_drps_mss()

# Run script
if __name__ == "__main__":
    processor = RTDP(rng_seed = None, directory=".", extension=".txt", log_file="logfile.txt", sim_config="cpu_sim.yaml")
    processor.sim()
    processor.plot_all()

#$ python
#Python 3.6.8 (default, Nov 15 2024, 08:11:39) 
#[GCC 8.5.0 20210514 (Red Hat 8.5.0-22)] on linux
#Type "help", "copyright", "credits" or "license" for more information.
#>>> from rtdp import RTDP
#>>> rtdp = RTDP(rng_seed=7, log_file="z.txt")
#>>> rtdp.sim()
#>>> rtdp.emulate(file="cpu_emu.yaml", prog="cpu_emu")   # deploys daisy chain
#>>> rtdp.send_emu(frm_sz=2, frm_cnt=100, bit_rt=10)  # triggers sender on first host

#>>> rtdp.plot_all()
#$ for f in *.png; do eog "$f" & done
#$ less z.txt
#$ killall eog
#$ rm *png *txt

# Example blah.yaml

# cmpnt_cnt: 10
# base_port: 6000
# avg-rate: 20
# frm_cnt: 500
# frm_sz: 1
# bit_rt: 10
# hosts:
#   - 192.168.1.10
#   - 192.168.1.11
#   - 192.168.1.12

# bp = BatchProcessor()
# bp.emulate(file="blah.yaml", prog="xyz")   # deploys daisy chain
# bp.send_emu(frm_sz=2, frm_cnt=1000, bit_rt=50)  # triggers sender on first host


