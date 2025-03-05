import java.util.concurrent.TimeUnit;

import org.jlab.epsci.ersap.base.BaseOrchestrator;
import org.jlab.epsci.ersap.base.ContainerName;
import org.jlab.epsci.ersap.base.ServiceName;
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.base.ErsapLang;
import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.base.core.ErsapComponent;

import java.util.concurrent.TimeoutException;

/**
 * A simplified Core class for ERSAP applications.
 * This class provides basic functionality to interact with ERSAP services.
 */
public class Core {
    private final BaseOrchestrator orchestrator;
    private final String frontEnd;

    /**
     * Creates a new Core instance with default settings.
     */
    public Core() {
        this.orchestrator = new BaseOrchestrator();
        this.frontEnd = "localhost";
    }

    /**
     * Creates a new Core instance with the specified front-end.
     *
     * @param frontEnd the front-end DPE host
     */
    public Core(String frontEnd) {
        this.orchestrator = new BaseOrchestrator();
        this.frontEnd = frontEnd;
    }

    /**
     * Starts a container with the given name.
     *
     * @param containerName the name of the container
     * @throws ErsapException if there is an error starting the container
     */
    public void startContainer(String containerName) throws ErsapException {
        ErsapComponent component = ErsapComponent.container(frontEnd, containerName, 1, "");
        ContainerName container = new ContainerName(component.getDpeHost(), ErsapLang.JAVA, containerName);
        orchestrator.deploy(container);
    }

    /**
     * Starts a service with the given name in the specified container.
     *
     * @param containerName the name of the container
     * @param serviceClass the class name of the service
     * @param serviceName the name of the service
     * @throws ErsapException if there is an error starting the service
     */
    public void startService(String containerName, String serviceClass, String serviceName) throws ErsapException {
        ErsapComponent containerComponent = ErsapComponent.container(frontEnd, containerName, 1, "");
        ContainerName container = new ContainerName(containerComponent.getDpeHost(), ErsapLang.JAVA, containerName);
        ServiceName service = new ServiceName(container, serviceName);
        orchestrator.deploy(service, serviceClass);
    }

    /**
     * Configures a service with the given data.
     *
     * @param serviceAddress the address of the service in the format "container:service"
     * @param data the configuration data
     * @throws ErsapException if there is an error configuring the service
     */
    public void configure(String serviceAddress, EngineData data) throws ErsapException {
        try {
            String[] parts = serviceAddress.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid service address: " + serviceAddress);
            }
            
            ErsapComponent containerComponent = ErsapComponent.container(frontEnd, parts[0], 1, "");
            ContainerName container = new ContainerName(containerComponent.getDpeHost(), ErsapLang.JAVA, parts[0]);
            ServiceName service = new ServiceName(container, parts[1]);
            
            // Use the configure method directly
            EngineData response = orchestrator.configure(service).withData(data).syncRun(10, TimeUnit.SECONDS);
            
            if (response.getStatus() != EngineStatus.INFO) {
                throw new ErsapException("Error configuring service: " + response.getData());
            }
        } catch (Exception e) {
            throw new ErsapException("Error configuring service: " + e.getMessage(), e);
        }
    }

    /**
     * Sends data to a service and waits for a response.
     *
     * @param serviceAddress the address of the service in the format "container:service"
     * @param data the data to send
     * @return the response from the service
     * @throws ErsapException if there is an error sending the data
     */
    public EngineData send(String serviceAddress, EngineData data) throws ErsapException {
        try {
            String[] parts = serviceAddress.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid service address: " + serviceAddress);
            }
            
            ErsapComponent containerComponent = ErsapComponent.container(frontEnd, parts[0], 1, "");
            ContainerName container = new ContainerName(containerComponent.getDpeHost(), ErsapLang.JAVA, parts[0]);
            ServiceName service = new ServiceName(container, parts[1]);
            
            // Use the execute method directly
            return orchestrator.execute(service).withData(data).syncRun(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new ErsapException("Error sending data to service: " + e.getMessage(), e);
        }
    }

    /**
     * Stops a service.
     *
     * @param containerName the name of the container
     * @param serviceName the name of the service
     * @throws ErsapException if there is an error stopping the service
     */
    public void stopService(String containerName, String serviceName) throws ErsapException {
        ErsapComponent containerComponent = ErsapComponent.container(frontEnd, containerName, 1, "");
        ContainerName container = new ContainerName(containerComponent.getDpeHost(), ErsapLang.JAVA, containerName);
        ServiceName service = new ServiceName(container, serviceName);
        orchestrator.exit(service);
    }

    /**
     * Stops a container.
     *
     * @param containerName the name of the container
     * @throws ErsapException if there is an error stopping the container
     */
    public void stopContainer(String containerName) throws ErsapException {
        ErsapComponent containerComponent = ErsapComponent.container(frontEnd, containerName, 1, "");
        ContainerName container = new ContainerName(containerComponent.getDpeHost(), ErsapLang.JAVA, containerName);
        orchestrator.exit(container);
    }

    /**
     * Closes the core and releases all resources.
     */
    public void close() {
        orchestrator.close();
    }
}