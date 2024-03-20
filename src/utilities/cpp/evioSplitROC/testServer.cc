#include <iostream>
#include <cstring>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>

int main() {
    // Create a socket
    int serverSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (serverSocket == -1) {
        std::cerr << "Error creating socket\n";
        return 1;
    }

    // Set up the server address
    struct sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(8080); // Port number 8080
    serverAddr.sin_addr.s_addr = INADDR_ANY; // Accept connections from any IP address

    // Bind the socket to the server address
    if (bind(serverSocket, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) == -1) {
        std::cerr << "Error binding socket\n";
        close(serverSocket);
        return 1;
    }

    // Listen for incoming connections
    if (listen(serverSocket, 5) == -1) { // Queue up to 5 incoming connections
        std::cerr << "Error listening for connections\n";
        close(serverSocket);
        return 1;
    }

    std::cout << "Server is listening on port 8080...\n";

    // Accept incoming connections and process data
    while (true) {
        struct sockaddr_in clientAddr;
        socklen_t clientAddrLen = sizeof(clientAddr);
        int clientSocket = accept(serverSocket, (struct sockaddr*)&clientAddr, &clientAddrLen);
        if (clientSocket == -1) {
            std::cerr << "Error accepting connection\n";
            close(serverSocket);
            return 1;
        }

        std::cout << "Client connected\n";

        char buffer[1024];
        ssize_t bytesRead;

        // Receive data from client and process it
        while ((bytesRead = recv(clientSocket, buffer, sizeof(buffer), 0)) > 0) {
            // Process the received data (e.g., print or save to a file)
            std::cout << "Received " << bytesRead << " bytes from client: " << std::endl;
            // Echo back to the client
            send(clientSocket, "ACK", sizeof("ACK"), 0);
        }

        if (bytesRead == -1) {
            std::cerr << "Error receiving data\n";
        } else {
            std::cout << "Client disconnected\n";
        }

        close(clientSocket); // Close the client socket
    }

    close(serverSocket); // Close the server socket (this won't be reached in an infinite loop)

    return 0;
}
