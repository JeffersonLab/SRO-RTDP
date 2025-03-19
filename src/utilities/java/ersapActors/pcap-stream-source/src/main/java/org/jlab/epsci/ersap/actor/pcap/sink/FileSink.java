package org.jlab.epsci.ersap.actor.pcap.sink;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.actor.pcap.proc.PcapPacketProcessor.ProcessedPacket;

/**
 * A sink that writes processed PCAP data to a file.
 */
public class FileSink implements IESink {
    
    private static final Logger LOGGER = Logger.getLogger(FileSink.class.getName());
    
    private String outputDirectory;
    private String filePrefix;
    private String fileExtension;
    private long maxFileSize;
    private boolean appendTimestamp;
    
    private File currentFile;
    private ObjectOutputStream outputStream;
    private long bytesWritten;
    private boolean isOpen;
    
    /**
     * Default constructor.
     */
    public FileSink() {
        this.outputDirectory = "output";
        this.filePrefix = "pcap_processed";
        this.fileExtension = ".dat";
        this.maxFileSize = 100 * 1024 * 1024; // 100 MB
        this.appendTimestamp = true;
        this.bytesWritten = 0;
        this.isOpen = false;
    }
    
    /**
     * Constructor with parameters.
     * 
     * @param outputDirectory The directory to write files to
     * @param filePrefix The prefix for output files
     * @param fileExtension The extension for output files
     * @param maxFileSize The maximum size of each file in bytes
     * @param appendTimestamp Whether to append a timestamp to file names
     */
    public FileSink(String outputDirectory, String filePrefix, String fileExtension, 
                   long maxFileSize, boolean appendTimestamp) {
        this.outputDirectory = outputDirectory;
        this.filePrefix = filePrefix;
        this.fileExtension = fileExtension;
        this.maxFileSize = maxFileSize;
        this.appendTimestamp = appendTimestamp;
        this.bytesWritten = 0;
        this.isOpen = false;
    }
    
    @Override
    public void open() throws IOException {
        // Create output directory if it doesn't exist
        Path dirPath = Paths.get(outputDirectory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        
        // Create a new output file
        createNewOutputFile();
        
        isOpen = true;
    }
    
    @Override
    public void close() throws IOException {
        if (outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
        isOpen = false;
    }
    
    @Override
    public void write(Object data) throws IOException {
        if (!isOpen) {
            throw new IOException("Sink is not open");
        }
        
        // Check if we need to create a new file due to size limit
        if (bytesWritten >= maxFileSize) {
            // Close current file
            outputStream.close();
            
            // Create a new file
            createNewOutputFile();
            
            bytesWritten = 0;
        }
        
        // Write the data
        if (data instanceof ProcessedPacket) {
            outputStream.writeObject(data);
            
            // Estimate size (rough approximation)
            ProcessedPacket packet = (ProcessedPacket) data;
            bytesWritten += (packet.getRawData() != null ? packet.getRawData().length : 0) + 100; // Add overhead
        } else {
            // For other types, just write as bytes if possible
            if (data instanceof byte[]) {
                byte[] bytes = (byte[]) data;
                outputStream.write(bytes);
                bytesWritten += bytes.length;
            } else {
                // Try to write as a serializable object
                outputStream.writeObject(data);
                // Rough size estimate
                bytesWritten += 100;
            }
        }
    }
    
    @Override
    public void flush() throws IOException {
        if (outputStream != null) {
            outputStream.flush();
        }
    }
    
    @Override
    public boolean isOpen() {
        return isOpen;
    }
    
    /**
     * Creates a new output file with the configured naming pattern.
     */
    private void createNewOutputFile() throws IOException {
        StringBuilder fileName = new StringBuilder();
        fileName.append(filePrefix);
        
        if (appendTimestamp) {
            fileName.append("_").append(System.currentTimeMillis());
        }
        
        fileName.append(fileExtension);
        
        currentFile = new File(outputDirectory, fileName.toString());
        LOGGER.info("Creating new output file: " + currentFile.getAbsolutePath());
        
        outputStream = new ObjectOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(currentFile)));
    }
    
    /**
     * Set the output directory.
     */
    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
    
    /**
     * Set the file prefix.
     */
    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }
    
    /**
     * Set the file extension.
     */
    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }
    
    /**
     * Set the maximum file size in bytes.
     */
    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }
    
    /**
     * Set whether to append a timestamp to file names.
     */
    public void setAppendTimestamp(boolean appendTimestamp) {
        this.appendTimestamp = appendTimestamp;
    }
    
    /**
     * Get the current file being written to.
     */
    public File getCurrentFile() {
        return currentFile;
    }
    
    /**
     * Get the number of bytes written to the current file.
     */
    public long getBytesWritten() {
        return bytesWritten;
    }
} 