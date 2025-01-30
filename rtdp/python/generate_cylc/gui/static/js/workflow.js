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

        // Create edge data
        const edge = {
            from: edgeData.from,
            to: edgeData.to,
            label: 'ready',
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
        formData.append('type', 'ready');

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
                } else {
                    callback(null);
                }
            })
            .catch(error => {
                console.error('Error adding edge:', error);
                callback(null);
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

    openComponentConfig(nodeId) {
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

        // Get the form
        const form = modal.querySelector('form');
        if (!form) return;

        // Set the component ID
        const idInput = form.querySelector('input[name="id"]');
        if (idInput) {
            idInput.value = nodeId;
        }

        // Show the modal
        const bsModal = new bootstrap.Modal(modal);
        bsModal.show();
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
        // Clear existing nodes and edges
        this.nodes.clear();
        this.edges.clear();

        // Add nodes from components
        Object.entries(config.components).forEach(([id, comp]) => {
            this.nodes.add({
                id: id,
                label: `${comp.type}\n#${id}`,
                type: comp.type,
                color: this.getNodeColor(comp.type)
            });
        });

        // Add edges
        if (config.edges && Array.isArray(config.edges)) {
            config.edges.forEach(edge => {
                this.edges.add({
                    from: edge.from,
                    to: edge.to,
                    label: edge.type || 'ready',
                    arrows: {
                        to: {
                            enabled: true,
                            scaleFactor: 1
                        }
                    }
                });
            });
        }

        // Stabilize the network
        this.network.stabilize();
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