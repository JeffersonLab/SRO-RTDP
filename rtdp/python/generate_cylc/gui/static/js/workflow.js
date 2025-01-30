// Using vis-network for interactive graph editing with drag-and-drop functionality
class WorkflowGraph {
    constructor() {
        if (typeof vis === 'undefined') {
            throw new Error('vis-network library not loaded');
        }

        this.network = null;
        this.nodes = new vis.DataSet();
        this.edges = new vis.DataSet();
        this.graphContainer = document.getElementById('workflow-graph');

        // Define valid edge rules based on workflow dependencies
        // A -> B means "if A ready then start B"
        this.edgeRules = {
            'sender': ['emulator', 'receiver'],
            'emulator': ['receiver', 'sender', 'emulator'],  // Allow emulator to connect to sender and other emulators
            'receiver': ['sender', 'emulator']
        };

        this.init();
        this.setupDragAndDrop();
    }

    init() {
        const options = {
            nodes: {
                shape: 'box',
                margin: 10,
                font: {
                    size: 14,
                    face: 'arial',
                    align: 'center'
                },
                borderWidth: 2,
                shadow: true
            },
            edges: {
                arrows: {
                    to: {
                        enabled: true,
                        scaleFactor: 1
                    }
                },
                smooth: {
                    enabled: true,
                    type: 'cubicBezier',
                    roundness: 0.5
                },
                font: {
                    size: 12,
                    align: 'middle'
                },
                color: {
                    color: '#2B7CE9',
                    highlight: '#2B7CE9',
                    hover: '#2B7CE9'
                }
            },
            manipulation: {
                enabled: true,
                addEdge: (edgeData, callback) => {
                    this.handleEdgeCreation(edgeData, callback);
                },
                deleteEdge: (edgeData, callback) => {
                    this.handleEdgeDeletion(edgeData, callback);
                },
                deleteNode: (nodeData, callback) => {
                    this.handleNodeDeletion(nodeData, callback);
                }
            },
            physics: {
                enabled: true,
                solver: 'forceAtlas2Based',
                forceAtlas2Based: {
                    gravitationalConstant: -50,
                    springLength: 200,
                    springConstant: 0.1
                },
                stabilization: {
                    enabled: true,
                    iterations: 1000
                }
            }
        };

        this.network = new vis.Network(
            this.graphContainer,
            { nodes: this.nodes, edges: this.edges },
            options
        );

        // Handle node selection for configuration
        this.network.on('doubleClick', (params) => {
            if (params.nodes.length > 0) {
                this.openComponentConfig(params.nodes[0]);
            } else if (params.edges.length > 0) {
                this.openEdgeConfig(params.edges[0]);
            }
        });

        // Disable physics after initial layout
        this.network.once('stabilized', () => {
            this.network.setOptions({ physics: { enabled: false } });
        });
    }

    setupDragAndDrop() {
        const paletteItems = document.querySelectorAll('.palette-item');
        const graphContainer = this.graphContainer;

        paletteItems.forEach(item => {
            item.addEventListener('dragstart', (e) => {
                const type = item.getAttribute('data-type');
                e.dataTransfer.setData('componentType', type);
            });
        });

        graphContainer.addEventListener('dragover', (e) => {
            e.preventDefault();
            graphContainer.classList.add('drop-target');
        });

        graphContainer.addEventListener('dragleave', () => {
            graphContainer.classList.remove('drop-target');
        });

        graphContainer.addEventListener('drop', (e) => {
            e.preventDefault();
            graphContainer.classList.remove('drop-target');

            const type = e.dataTransfer.getData('componentType');
            if (!type) return;

            const rect = graphContainer.getBoundingClientRect();
            const pos = this.network.DOMtoCanvas({
                x: e.clientX - rect.left,
                y: e.clientY - rect.top
            });

            this.addComponent(type, pos);
        });
    }

    async addComponent(type, position) {
        try {
            // Get current configuration from backend
            const configResponse = await fetch('/api/workflow/config');
            const config = await configResponse.json();

            // Get existing nodes from both visual graph and backend
            const visualNodes = this.nodes.get({
                filter: node => node.type === type
            });
            const backendNodes = Object.keys(config.components || {})
                .filter(id => id.startsWith(`${type}-`))
                .map(id => {
                    const match = id.match(new RegExp(`${type}-(\\d+)`));
                    return match ? parseInt(match[1]) : 0;
                });

            // Combine and sort all used numbers
            const usedNumbers = [...new Set([
                ...visualNodes.map(node => {
                    const match = node.id.match(new RegExp(`${type}-(\\d+)`));
                    return match ? parseInt(match[1]) : 0;
                }),
                ...backendNodes
            ])].sort((a, b) => a - b);

            // Find the first available number
            let nextNumber = 1;
            for (const num of usedNumbers) {
                if (nextNumber < num) {
                    break;
                }
                nextNumber = num + 1;
            }

            if (nextNumber > 999) {
                alert('Maximum number of components reached (999)');
                return;
            }

            const id = `${type}-${nextNumber}`;

            // Double check the ID is truly unique
            if (config.components && config.components[id]) {
                throw new Error('Component ID collision detected');
            }

            const nodeData = {
                id: id,
                label: `${type}\n#${nextNumber}`,
                x: position.x,
                y: position.y,
                type: type,
                color: this.getNodeColor(type)
            };

            // Create form data for the request
            const formData = new FormData();
            formData.append('id', id);
            formData.append('type', type);
            formData.append('partition', 'ifarm');
            formData.append('cpus_per_task', '4');
            formData.append('mem', '8G');

            // Make API call to add component
            const response = await fetch('/api/components', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                throw new Error('Failed to add component');
            }

            const data = await response.json();
            if (data.status === 'success') {
                this.nodes.add(nodeData);
            }
        } catch (error) {
            console.error('Error adding component:', error);
            alert('Failed to add component. Please try again.');
        }
    }

    handleEdgeCreation(edgeData, callback) {
        const fromNode = this.nodes.get(edgeData.from);
        const toNode = this.nodes.get(edgeData.to);

        if (!fromNode || !toNode) {
            callback(null);
            return;
        }

        // Check if this connection is allowed based on data flow rules
        if (fromNode.type === 'receiver') {
            alert('Receiver cannot be a data producer');
            callback(null);
            return;
        }
        if (toNode.type === 'sender') {
            alert('Sender cannot be a data consumer');
            callback(null);
            return;
        }

        // Check for duplicate edges
        const existingEdges = this.edges.get({
            filter: edge => edge.from === edgeData.from && edge.to === edgeData.to
        });

        if (existingEdges.length > 0) {
            alert('Data flow already exists between these components');
            callback(null);
            return;
        }

        // Open a dialog to get the data flow description
        const description = prompt('Enter a description of the data being transferred:');
        if (!description) {
            callback(null);
            return;
        }

        // Create form data for the request
        const formData = new FormData();
        formData.append('from_id', edgeData.from);
        formData.append('to_id', edgeData.to);
        formData.append('description', description);

        // Make API call to add edge
        fetch('/api/edges', {
            method: 'POST',
            body: formData
        })
            .then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    edgeData.label = description;
                    callback(edgeData);
                } else {
                    alert(data.message || 'Failed to add data flow');
                    callback(null);
                }
            })
            .catch(error => {
                console.error('Error adding data flow:', error);
                alert('Failed to add data flow. Please try again.');
                callback(null);
            });
    }

    handleEdgeDeletion(edgeData, callback) {
        // Handle both single and multiple edge deletion
        const edgeIds = Array.isArray(edgeData.edges) ? edgeData.edges : [edgeData.edges[0]];

        // Create a promise for each edge deletion
        const deletePromises = edgeIds.map(edgeId => {
            const edge = this.edges.get(edgeId);
            if (!edge) {
                return Promise.reject(new Error(`Edge ${edgeId} not found`));
            }
            return fetch(`/api/edges/${edge.from}/${edge.to}`, {
                method: 'DELETE'
            })
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`Failed to delete edge from ${edge.from} to ${edge.to}`);
                    }
                    return response.json();
                });
        });

        // Wait for all deletions to complete
        Promise.all(deletePromises)
            .then(() => {
                // Remove all edges from the graph
                this.edges.remove(edgeIds);
                callback(edgeData);

                // Wait a short moment before refreshing to ensure backend is updated
                setTimeout(() => {
                    refreshWorkflowGraph();
                }, 100);
            })
            .catch(error => {
                console.error('Error deleting edges:', error);
                alert('Failed to delete some edges. Please try again.');
                callback(null);
            });
    }

    handleNodeDeletion(nodeData, callback) {
        // Handle both single and multiple node deletion
        const nodeIds = Array.isArray(nodeData.nodes) ? nodeData.nodes : [nodeData.nodes[0]];

        // Create a promise for each node deletion
        const deletePromises = nodeIds.map(nodeId =>
            fetch(`/api/components/${nodeId}`, {
                method: 'DELETE'
            })
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`Failed to delete component ${nodeId}`);
                    }
                    return response.json();
                })
        );

        // Wait for all deletions to complete
        Promise.all(deletePromises)
            .then(() => {
                // Remove all nodes from the graph
                this.nodes.remove(nodeIds);
                callback(nodeData);

                // Wait a short moment before refreshing to ensure backend is updated
                setTimeout(() => {
                    refreshWorkflowGraph();
                }, 100);
            })
            .catch(error => {
                console.error('Error deleting components:', error);
                alert('Failed to delete some components. Please try again.');
                callback(null);
            });
    }

    async openComponentConfig(nodeId) {
        const node = this.nodes.get(nodeId);
        if (!node) return;

        // Get the modal
        const modal = document.getElementById('componentModal');
        if (!modal) return;

        // Update modal title
        const modalTitle = modal.querySelector('.modal-title');
        if (modalTitle) {
            modalTitle.textContent = `Configure ${node.type} Component`;
        }

        try {
            // Fetch current component configuration
            const response = await fetch('/api/workflow/config');
            const config = await response.json();
            const componentConfig = config.components[nodeId];

            if (!componentConfig) {
                throw new Error('Component configuration not found');
            }

            // Get the form container
            const formContainer = modal.querySelector('.modal-body');
            if (!formContainer) return;

            // Create form HTML based on component type
            let formHtml = `
                <form id="componentForm">
                    <input type="hidden" name="id" value="${nodeId}">
                    <input type="hidden" name="type" value="${node.type}">
                    
                    <!-- Common Resources Section -->
                    <div class="mb-3">
                        <h5>Resources</h5>
                        <div class="mb-2">
                            <label class="form-label">Partition</label>
                            <input type="text" class="form-control" name="partition" value="${componentConfig.resources.partition}" required>
                        </div>
                        <div class="mb-2">
                            <label class="form-label">CPUs per Task</label>
                            <input type="number" class="form-control" name="cpus_per_task" value="${componentConfig.resources.cpus_per_task}" min="1" required>
                        </div>
                        <div class="mb-2">
                            <label class="form-label">Memory</label>
                            <input type="text" class="form-control" name="mem" value="${componentConfig.resources.mem}" required>
                        </div>
                    </div>`;

            // Add Network Section based on component type
            if (node.type === 'receiver') {
                formHtml += `
                    <!-- Network Section -->
                    <div class="mb-3">
                        <h5>Network</h5>
                        <div class="mb-2">
                            <label class="form-label">Port</label>
                            <input type="number" class="form-control" name="port" min="1024" max="65535" 
                                value="${componentConfig.network ? componentConfig.network.port : ''}">
                        </div>
                        <div class="mb-2">
                            <label class="form-label">Bind Address</label>
                            <input type="text" class="form-control" name="bind_address" 
                                value="${componentConfig.network ? componentConfig.network.bind_address : '0.0.0.0'}">
                        </div>
                    </div>`;
            } else if (node.type === 'emulator') {
                formHtml += `
                    <!-- Network Section -->
                    <div class="mb-3">
                        <h5>Network</h5>
                        <div class="mb-2">
                            <label class="form-label">Listen Port</label>
                            <input type="number" class="form-control" name="port" min="1024" max="65535" 
                                value="${componentConfig.network ? componentConfig.network.listen_port : ''}">
                        </div>
                    </div>`;
            }

            // Add type-specific configuration
            if (node.type === 'emulator') {
                const emulatorConfig = componentConfig.configuration || {
                    threads: 4,
                    latency: 50,
                    mem_footprint: 0.05,
                    output_size: 0.001
                };
                formHtml += `
                    <!-- Emulator Configuration -->
                    <div class="mb-3">
                        <h5>Emulator Configuration</h5>
                        <div class="mb-2">
                            <label class="form-label">Threads</label>
                            <input type="number" class="form-control" name="threads" value="${emulatorConfig.threads}" min="1">
                        </div>
                        <div class="mb-2">
                            <label class="form-label">Latency (ms)</label>
                            <input type="number" class="form-control" name="latency" value="${emulatorConfig.latency}" min="0">
                        </div>
                        <div class="mb-2">
                            <label class="form-label">Memory Footprint</label>
                            <input type="number" class="form-control" name="mem_footprint" value="${emulatorConfig.mem_footprint}" step="0.01" min="0">
                        </div>
                        <div class="mb-2">
                            <label class="form-label">Output Size</label>
                            <input type="number" class="form-control" name="output_size" value="${emulatorConfig.output_size}" step="0.001" min="0">
                        </div>
                    </div>`;
            } else if (node.type === 'sender') {
                const testData = componentConfig.test_data || { size: '100M' };
                formHtml += `
                    <!-- Sender Configuration -->
                    <div class="mb-3">
                        <h5>Test Data</h5>
                        <div class="mb-2">
                            <label class="form-label">Data Size</label>
                            <input type="text" class="form-control" name="data_size" value="${testData.size}">
                        </div>
                    </div>`;
            }

            formHtml += `
                    <div class="text-end">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                        <button type="submit" class="btn btn-primary">Save</button>
                    </div>
                </form>`;

            formContainer.innerHTML = formHtml;

            // Add form submit handler
            const form = formContainer.querySelector('form');
            form.addEventListener('submit', async (e) => {
                e.preventDefault();
                const formData = new FormData(form);

                try {
                    const response = await fetch('/api/components/' + nodeId, {
                        method: 'POST',
                        body: formData
                    });

                    if (!response.ok) {
                        throw new Error('Failed to update component');
                    }

                    const data = await response.json();
                    if (data.status === 'success') {
                        // Close modal
                        bootstrap.Modal.getInstance(modal).hide();
                        // Refresh graph
                        refreshWorkflowGraph();
                    }
                } catch (error) {
                    console.error('Error updating component:', error);
                    alert('Failed to update component configuration');
                }
            });

            // Show the modal
            const bsModal = new bootstrap.Modal(modal);
            bsModal.show();
        } catch (error) {
            console.error('Error loading component configuration:', error);
            alert('Failed to load component configuration');
        }
    }

    openEdgeConfig(edgeId) {
        const edge = this.edges.get(edgeId);
        if (!edge) return;

        // Open a dialog to edit the data flow description
        const description = prompt('Edit the description of the data being transferred:', edge.label);
        if (description === null) return; // User cancelled

        // Create form data for the request
        const formData = new FormData();
        formData.append('from_id', edge.from);
        formData.append('to_id', edge.to);
        formData.append('description', description);

        // Make API call to update edge
        fetch('/api/edges', {
            method: 'POST',
            body: formData
        })
            .then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    edge.label = description;
                    this.edges.update(edge);
                } else {
                    alert(data.message || 'Failed to update data flow');
                }
            })
            .catch(error => {
                console.error('Error updating data flow:', error);
                alert('Failed to update data flow. Please try again.');
            });
    }

    getNodeColor(type) {
        const colors = {
            'sender': { background: '#90EE90', border: '#60c060' },
            'receiver': { background: '#F08080', border: '#d05050' },
            'emulator': { background: '#ADD8E6', border: '#7ab5cc' }
        };
        return colors[type] || { background: '#ffffff', border: '#666666' };
    }

    updateFromConfig(config) {
        // Disable physics before any changes
        this.network.setOptions({ physics: { enabled: false } });

        // Store current positions of nodes
        const positions = {};
        this.nodes.forEach(node => {
            const position = this.network.getPosition(node.id);
            positions[node.id] = position;
        });

        // Clear existing nodes and edges
        this.nodes.clear();
        this.edges.clear();

        // Add nodes from components, preserving positions if they existed
        Object.entries(config.components).forEach(([id, comp]) => {
            const nodeData = {
                id: id,
                label: `${comp.type}\n#${id}`,
                type: comp.type,
                color: this.getNodeColor(comp.type),
                physics: false  // Disable physics for each node
            };

            // If this node existed before, restore its position
            if (positions[id]) {
                nodeData.x = positions[id].x;
                nodeData.y = positions[id].y;
            }

            this.nodes.add(nodeData);
        });

        // Add edges with their types and conditions
        if (config.edges && Array.isArray(config.edges)) {
            config.edges.forEach(edge => {
                const edgeLabel = edge.type + (edge.condition ? `\n(${edge.condition})` : '');
                this.edges.add({
                    from: edge.from,
                    to: edge.to,
                    label: edgeLabel,
                    arrows: {
                        to: {
                            enabled: true,
                            scaleFactor: 1
                        }
                    },
                    physics: false  // Disable physics for edges too
                });
            });
        }

        // No need to stabilize or fix/unfix nodes since physics is disabled
    }
}

// Initialize graph and handle updates
let workflowGraph;

function initializeWorkflowGraph() {
    workflowGraph = new WorkflowGraph();
    refreshWorkflowGraph();
}

async function refreshWorkflowGraph() {
    try {
        const response = await fetch('/api/workflow/config');
        const config = await response.json();
        workflowGraph.updateFromConfig(config);
    } catch (error) {
        console.error('Failed to refresh workflow graph:', error);
    }
} 