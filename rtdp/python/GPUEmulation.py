import numpy as np
import time,sys

class GPU:
    
    def __init__(self,data,mean,deviation):
        self.data = data 
        self.mean = mean 
        self.deviation = deviation 

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
        print("\n")

    def processingDelay(self):
        random_delay = np.random.normal(self.mean, self.deviation)
        # Ensure the random delay is non-negative (delay cannot be negative)
        random_delay = max(0, random_delay)
        self.progress_bar(random_delay) # time spend inside of the GPU
        return 

    def gpu_Component(self):
        print ("\t\t Obtained Data from CPU \t\t \n")
        print ("\t\t Processing the data \t\t \n")
        self.processingDelay()
        print (" \t\t Processed data. Sending data back to CPU component \t\t\n")
        return 

class CPU:
    
    def __init__(self,data, mean, deviation):
        self.data = data
        self.mean = mean
        self.deviation = deviation

    def cpu_component(self):
        print ("\t\tInside of the CPU component\t\t\n")
        gpucomponent = GPU(self.data, self.mean, self.deviation)
        gpucomponent.gpu_Component()
        print ()
        return

if __name__ == "__main__" :
    data = {1,2,3,4,5}
    meanDelay = input ("Enter the mean delay: ")
    stdDeviationDelay = input ("Enter the standard deviation of the delay: ")
    cpucomponent = CPU(data, int(meanDelay), int(stdDeviationDelay))
    cpucomponent.cpu_component()
    print ("\t\t Data received from emulated GPU \t\t \n")