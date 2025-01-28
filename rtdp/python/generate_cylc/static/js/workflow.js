// Workflow Graph Manager
class WorkflowGraph {
    constructor() {
        if (typeof vis === 'undefined') {
            throw new Error('vis-network library not loaded');
        }

        this.network = null;
        this.components = {};
        this.connections = [];
        this.graphContainer = document.getElementById('workflowGraph');

        if (!this.graphContainer) {
            throw new Error('Graph container not found');
        }

        this.init();
        this.setupDragAndDrop();
    }

    init() {
        try {
            const container = this.graphContainer;
            const options = {
                autoResize: true,
                height: '100%',
                width: '100%',
                physics: {
                    enabled: false
                },
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
                    }
                },
                interaction: {
                    dragNodes: true,
                    dragView: true,
                    zoomView: true,
                    hover: true,
                    multiselect: true
                }
            };

            this.network = new vis.Network(
                container,
                {
                    nodes: new vis.DataSet(),
                    edges: new vis.DataSet()
                },
                options
            );

            this.network.on('doubleClick', (params) => {
                if (params.nodes.length > 0) {
                    this.openComponentConfig(params.nodes[0]);
                }
            });
        } catch (error) {
            console.error('Error initializing network:', error);
            throw error;
        }
    }

    setupDragAndDrop() {
        const paletteItems = document.querySelectorAll('.palette-item');
        const graphContainer = this.graphContainer;

        // Add drag start event to palette items
        paletteItems.forEach(item => {
            item.addEventListener('dragstart', (e) => {
                const type = item.getAttribute('data-type');
                e.dataTransfer.setData('componentType', type);
                e.dataTransfer.effectAllowed = 'copy';

                // Create a drag image
                const dragImage = item.querySelector('.node').cloneNode(true);
                dragImage.style.width = '100px';
                document.body.appendChild(dragImage);
                e.dataTransfer.setDragImage(dragImage, 50, 25);
                setTimeout(() => dragImage.remove(), 0);
            });
        });

        // Prevent default to allow drop
        graphContainer.addEventListener('dragenter', (e) => {
            e.preventDefault();
            graphContainer.classList.add('drop-target');
        });

        graphContainer.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
        });

        graphContainer.addEventListener('dragleave', (e) => {
            e.preventDefault();
            graphContainer.classList.remove('drop-target');
        });

        // Handle the drop
        graphContainer.addEventListener('drop', (e) => {
            e.preventDefault();
            graphContainer.classList.remove('drop-target');

            const type = e.dataTransfer.getData('componentType');
            if (!type) {
                console.error('No component type found in drop data');
                return;
            }

            const rect = graphContainer.getBoundingClientRect();
            const pos = this.network.DOMtoCanvas({
                x: e.clientX - rect.left,
                y: e.clientY - rect.top
            });

            this.addComponent(type, pos);
        });
    }

    addComponent(type, position) {
        const id = `${type}${Object.keys(this.components).length + 1}`;
        const nodeData = {
            id: id,
            label: `${type}\n#${id}`,
            x: position.x,
            y: position.y,
            fixed: {
                x: true,
                y: true
            },
            color: this.getNodeColor(type)
        };

        // Add the node to the network
        this.network.body.data.nodes.add(nodeData);
        this.components[id] = { type, config: {} };

        // Make API call to add component
        fetch('/api/components', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                name: id,
                type: type
            })
        })
            .then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    console.log('Component added successfully:', id);
                    // Release the fixed position after a short delay
                    setTimeout(() => {
                        this.network.body.data.nodes.update({
                            id: id,
                            fixed: {
                                x: false,
                                y: false
                            }
                        });
                    }, 500);
                }
            })
            .catch(error => {
                console.error('Error adding component:', error);
                // Remove the node if the API call failed
                this.network.body.data.nodes.remove(id);
                delete this.components[id];
            });
    }

    getNodeColor(type) {
        const colors = {
            sender: { background: '#90EE90', border: '#60c060' },
            receiver: { background: '#F08080', border: '#d05050' },
            emulator: { background: '#ADD8E6', border: '#7ab5cc' }
        };
        return colors[type] || { background: '#ffffff', border: '#666666' };
    }

    openComponentConfig(id) {
        const modal = new bootstrap.Modal(document.getElementById('componentModal'));
        const form = document.getElementById('componentFields');
        document.getElementById('componentId').value = id;

        // Generate form fields based on component type
        const component = this.components[id];
        if (!component) return;

        const defaultConfigs = {
            sender: {
                ntasks: 1,
                cpus_per_task: 4,
                mem: '8G',
                partition: 'ifarm',
                timeout: '2h'
            },
            receiver: {
                ntasks: 1,
                cpus_per_task: 4,
                mem: '8G',
                partition: 'ifarm',
                timeout: '2h'
            },
            emulator: {
                ntasks: 1,
                cpus_per_task: 4,
                mem: '16G',
                partition: 'ifarm',
                timeout: '2h',
                threads: 4,
                latency: 50,
                mem_footprint: 0.05,
                output_size: 0.001
            }
        };

        const config = component.config || defaultConfigs[component.type];
        let html = '';

        for (const [key, value] of Object.entries(config)) {
            html += `
                <div class="mb-3">
                    <label class="form-label">${key}</label>
                    <input type="${typeof value === 'number' ? 'number' : 'text'}" 
                           class="form-control" 
                           name="${key}" 
                           value="${value}"
                           ${typeof value === 'number' ? 'step="any"' : ''}>
                </div>
            `;
        }

        form.innerHTML = html;
        modal.show();
    }
}

// Remove the automatic initialization since we're doing it in index.html now 