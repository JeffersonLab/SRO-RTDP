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
            'emulator': ['receiver'],
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

    addComponent(type, position) {
        const id = `${type}-${Date.now()}`;
        const nodeData = {
            id: id,
            label: type,
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
        fetch('/api/components', {
            method: 'POST',
            body: formData
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to add component');
                }
                return response.json();
            })
            .then(data => {
                if (data.status === 'success') {
                    // Add node to the graph
                    this.nodes.add(nodeData);
                }
            })
            .catch(error => {
                console.error('Error adding component:', error);
            });
    }

    handleEdgeCreation(edgeData, callback) {
        const fromNode = this.nodes.get(edgeData.from);
        const toNode = this.nodes.get(edgeData.to);

        if (!fromNode || !toNode) {
            callback(null);
            return;
        }

        // Check if this connection is allowed
        const allowedTargets = this.edgeRules[fromNode.type] || [];
        if (!allowedTargets.includes(toNode.type)) {
            alert(`Invalid connection: ${fromNode.type} cannot trigger ${toNode.type}`);
            callback(null);
            return;
        }

        // Check for duplicate edges
        const existingEdges = this.edges.get({
            filter: edge => edge.from === edgeData.from && edge.to === edgeData.to
        });

        if (existingEdges.length > 0) {
            alert('This connection already exists');
            callback(null);
            return;
        }

        // Show edge configuration modal
        const modal = document.createElement('div');
        modal.className = 'modal fade';
        modal.innerHTML = `
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">Configure Edge</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <form id="edgeConfigForm">
                            <div class="mb-3">
                                <label class="form-label">Edge Type</label>
                                <select class="form-control" name="type" id="edgeType" required>
                                    <option value="ready">Ready (Start Chain)</option>
                                    <option value="succeeded">Succeeded (Completion Chain)</option>
                                    <option value="completed">Completed</option>
                                </select>
                            </div>
                            <div class="mb-3" id="conditionField" style="display: none;">
                                <label class="form-label">Condition</label>
                                <input type="text" class="form-control" name="condition" value="!" placeholder="e.g., ! for task completion">
                                <small class="form-text text-muted">Use ! for task completion in completion chain</small>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                        <button type="button" class="btn btn-primary" id="saveEdgeConfig">Save</button>
                    </div>
                </div>
            </div>
        `;

        document.body.appendChild(modal);
        const modalInstance = new bootstrap.Modal(modal);

        // Show/hide condition field based on edge type
        const edgeTypeSelect = modal.querySelector('#edgeType');
        const conditionField = modal.querySelector('#conditionField');
        edgeTypeSelect.addEventListener('change', () => {
            if (edgeTypeSelect.value === 'succeeded') {
                conditionField.style.display = 'block';
            } else {
                conditionField.style.display = 'none';
            }
        });

        modalInstance.show();

        // Handle edge configuration save
        document.getElementById('saveEdgeConfig').addEventListener('click', () => {
            const form = document.getElementById('edgeConfigForm');
            const edgeType = form.querySelector('[name="type"]').value;
            const condition = edgeType === 'succeeded' ? form.querySelector('[name="condition"]').value : '';

            // Create edge data
            const edge = {
                from: edgeData.from,
                to: edgeData.to,
                label: edgeType + (condition ? `\n(${condition})` : ''),
                arrows: {
                    to: {
                        enabled: true,
                        scaleFactor: 1
                    }
                }
            };

            // Create form data for the request
            const formData = new FormData();
            formData.append('from_id', edgeData.from);
            formData.append('to_id', edgeData.to);
            formData.append('type', edgeType);
            if (condition) {
                formData.append('condition', condition);
            }

            // Make API call to add edge
            fetch('/api/edges', {
                method: 'POST',
                body: formData
            })
                .then(response => response.json())
                .then(data => {
                    if (data.status === 'success') {
                        // Add edge to the graph
                        this.edges.add(edge);
                        callback(edge);
                        modalInstance.hide();
                        modal.remove();
                    } else {
                        callback(null);
                    }
                })
                .catch(error => {
                    console.error('Error adding edge:', error);
                    callback(null);
                });
        });

        // Clean up modal when hidden
        modal.addEventListener('hidden.bs.modal', () => {
            modal.remove();
            if (!this.edges.get(edgeData.id)) {
                callback(null);
            }
        });
    }

    handleEdgeDeletion(edgeData, callback) {
        const edge = edgeData.edges[0];
        // Make API call to delete edge
        fetch(`/api/edges/${edge.from}/${edge.to}`, {
            method: 'DELETE'
        })
            .then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    // Remove edge from the graph
                    this.edges.remove(edge);
                    callback(edge);
                } else {
                    callback(null);
                }
            })
            .catch(error => {
                console.error('Error deleting edge:', error);
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
                    </div>

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