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

        // Define valid edge rules based on Cylc workflow
        this.edgeRules = {
            'receiver': {
                'emulator': ['ready'],
            },
            'emulator': {
                'sender': ['ready'],
            },
            'sender': {
                'receiver': ['succeeded'],
            }
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
                arrows: 'to',
                smooth: {
                    enabled: true,
                    type: 'cubicBezier',
                    roundness: 0.5
                },
                font: {
                    size: 12,
                    align: 'middle'
                }
            },
            manipulation: {
                enabled: true,
                addEdge: (edgeData, callback) => {
                    this.handleEdgeCreation(edgeData, callback);
                }
            },
            physics: {
                enabled: false
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

        this.nodes.add(nodeData);

        // Create default configuration
        const resources = {
            partition: 'ifarm',
            cpus_per_task: 4,
            mem: '8G'
        };

        // Make API call to add component
        fetch('/api/components', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: id,
                type: type,
                resources: resources
            })
        })
            .catch(error => {
                console.error('Error adding component:', error);
                this.nodes.remove(id);
            });
    }

    handleEdgeCreation(edgeData, callback) {
        const fromNode = this.nodes.get(edgeData.from);
        const toNode = this.nodes.get(edgeData.to);

        if (!fromNode || !toNode) {
            callback(null);
            return;
        }

        const allowedTypes = this.edgeRules[fromNode.type]?.[toNode.type];
        if (!allowedTypes) {
            alert(`Invalid connection: ${fromNode.type} cannot connect to ${toNode.type}`);
            callback(null);
            return;
        }

        // Use the first allowed type
        const edgeType = allowedTypes[0];

        // Make API call to add edge
        fetch('/api/edges', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                from_id: edgeData.from,
                to_id: edgeData.to,
                type: edgeType
            })
        })
            .then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    edgeData.label = edgeType;
                    callback(edgeData);
                } else {
                    callback(null);
                }
            })
            .catch(error => {
                console.error('Error adding edge:', error);
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
                label: comp.type,
                type: comp.type,
                color: this.getNodeColor(comp.type)
            });
        });

        // Add edges
        config.edges.forEach(edge => {
            this.edges.add({
                from: edge.from,
                to: edge.to,
                label: edge.type
            });
        });
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