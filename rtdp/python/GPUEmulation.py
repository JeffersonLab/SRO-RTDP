import numpy as np
import time,sys,random

class GPU:
    __random_delay = None
    __sending_delay = 3 # delay to send the data to the CPU

    def __init__(self,data,minDelay,maxDelay):
        self.data = data 
        self.minDelay = minDelay 
        self.maxDelay = maxDelay 

    def progress_bar(self,duration):
        start_time = time.time()
        while True:
            elapsed_time = time.time() - start_time
            progress = min(elapsed_time / duration, 1.0)  # Ensure progress doesn't exceed 100%
            bar_length = 50
            filled_length = int(bar_length * progress)
            bar = '=' * filled_length + '-' * (bar_length - filled_length)
            sys.stdout.write(f"\rProgress: [{bar}] {progress * 100:.2f}%")
            sys.stdout.flush()
            if progress >= 1.0:
                break
            time.sleep(0.1)  # Update progress every 0.1 seconds
        print ()

    def processingDelay(self):
        self.random_delay = random.uniform(self.minDelay, self.maxDelay)
        self.progress_bar(self.random_delay) # time spend inside of the GPU
        return 
    
    def sendDataToCPU(self):
        scaleFactor = np.random.uniform(2, 4, size=(3, 4)) # row is 3 and col is 4 which is hardcoded for the POC
        modified_data = self.data * scaleFactor
        output_file = 'scaled_data.txt'
        with open(output_file, 'a') as file:
            # Append a header if needed
            file.write("Data Block :\n")
            # Append the scaled data using np.savetxt
            np.savetxt(file, modified_data, fmt='%.6f', delimiter=' ')
        time.sleep(self.__sending_delay)
        return 
    
    def gpu_Component(self):
        print ("\t\t Obtained Data from CPU \t\t ")
        print ("\t\t Processing the data \t\t ")
        self.processingDelay()
        print (" \t\t Processed data. Sending data back to CPU component \t\t")
        self.sendDataToCPU()
        return 

class CPU:
    __sendingDelay = 4 # delay to send the data to the GPU
    def __init__(self,seed, minDelay, maxDelay):
        self.seed = seed
        self.minDelay = minDelay
        self.maxDelay = maxDelay

    def cpu_component(self):
        while True:
            rows , columns  = 3,4
            data = np.random.rand(rows,columns)
            gpucomponent = GPU(data, self.minDelay, self.maxDelay)
            print ("\t\t Sending data to Emulated GPU")
            time.sleep(self.__sendingDelay)
            gpucomponent.gpu_Component()
            print ("---------------------- Received data from the GPU emulator ------------------ \n\n")
        return

if __name__ == "__main__" :
    seed = 10
    minDelay = input ("Enter the min delay: ")
    maxDelay = input ("Enter the max delay: ")
    cpucomponent = CPU(seed, int(minDelay), int(maxDelay))
    cpucomponent.cpu_component()