// Add this at the very top of the file
console.log('workflow.js loaded');

// Using vis-network for interactive graph editing with drag-and-drop functionality
class WorkflowGraph {
    constructor(container) {
        console.log('Initializing WorkflowGraph...');
        console.log('Container:', container);

        if (typeof vis === 'undefined') {
            console.error('vis-network library not loaded');
            throw new Error('vis-network library not loaded');
        }
        console.log('vis-network library found');

        this.container = container;
        this.network = null;
        this.nodes = new vis.DataSet();
        this.edges = new vis.DataSet();
        this.clipboard = { nodes: [], edges: [] };

        // Store unsaved changes
        this.unsavedChanges = {
            nodes: new Map(),
            edges: new Map()
        };

        // Define valid edge rules based on workflow dependencies
        // A -> B means "if A ready then start B"
        this.edgeRules = {
            'sender': ['emulator', 'receiver'],
            'emulator': ['receiver', 'sender', 'emulator'],  // Allow emulator to connect to sender and other emulators
            'receiver': ['sender', 'emulator']
        };

        console.log('Basic initialization complete');

        this.init();
        this.setupDragAndDrop();
        this.setupCopyPaste();
        this.setupSaveHandlers();
        this.setupFormHandlers();
        this.loadWorkflowState();  // Load initial state

        console.log('Full initialization complete');
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
                addNode: false,
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
            interaction: {
                dragNodes: true,
                dragView: true,
                multiselect: true,
                selectConnectedEdges: false
            },
            physics: false
        };

        this.network = new vis.Network(
            this.container,
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
    }

    setupDragAndDrop() {
        console.log('Setting up drag and drop handlers...');
        const paletteItems = document.querySelectorAll('.palette-item');
        const graphContainer = document.getElementById('workflow-graph');

        if (!graphContainer) {
            console.error('Graph container not found');
            return;
        }

        // Create a closure to maintain state
        const dragState = {
            currentType: null,
            isDragging: false
        };

        const handleDragStart = (e) => {
            console.log('DragStart event fired on:', e.target);

            // Get the palette item, whether it's the target or a parent
            const paletteItem = e.target.classList.contains('palette-item')
                ? e.target
                : e.target.closest('.palette-item');

            if (!paletteItem) {
                console.error('No palette item found');
                return;
            }

            const type = paletteItem.getAttribute('data-component-type');
            console.log('Component type for drag:', type);

            if (!type) {
                console.error('No component type found');
                return;
            }

            // Store the type in our closure
            dragState.currentType = type;
            dragState.isDragging = true;
            console.log('Stored current drag type:', dragState.currentType);

            // Set dragging styles
            paletteItem.style.opacity = '0.5';
            document.body.style.cursor = 'move';

            // Set data transfer
            e.dataTransfer.effectAllowed = 'copy';
            e.dataTransfer.setData('text/plain', type);
            e.dataTransfer.setData('application/json', JSON.stringify({ type: type }));

            // Create and set drag image
            const dragImage = paletteItem.cloneNode(true);
            dragImage.style.width = '150px';
            dragImage.style.height = '50px';
            dragImage.style.position = 'absolute';
            dragImage.style.top = '-1000px';
            dragImage.style.opacity = '0.8';
            document.body.appendChild(dragImage);
            e.dataTransfer.setDragImage(dragImage, 75, 25);
            setTimeout(() => document.body.removeChild(dragImage), 0);

            paletteItem.classList.add('dragging');
            console.log('Added dragging class to:', paletteItem);
        };

        const handleDrag = (e) => {
            e.preventDefault();
            console.log('Drag event fired, current type:', dragState.currentType, 'isDragging:', dragState.isDragging);
        };

        const handleDragEnd = (e) => {
            console.log('DragEnd event fired');
            e.preventDefault();

            // Get the palette item, whether it's the target or a parent
            const paletteItem = e.target.classList.contains('palette-item')
                ? e.target
                : e.target.closest('.palette-item');

            if (paletteItem) {
                paletteItem.classList.remove('dragging');
                paletteItem.style.opacity = '';
                document.body.style.cursor = '';
                console.log('Removed dragging class and reset styles');
            }
        };

        const handleDrop = (e) => {
            console.log('Drop event fired');
            e.preventDefault();
            graphContainer.classList.remove('drop-target');

            // Try to get the type from multiple sources
            let type = null;

            // 1. Try from our state
            type = dragState.currentType;
            console.log('Type from dragState:', type);

            // 2. Try from JSON data
            if (!type) {
                try {
                    const jsonData = e.dataTransfer.getData('application/json');
                    if (jsonData) {
                        const data = JSON.parse(jsonData);
                        type = data.type;
                        console.log('Type from JSON data:', type);
                    }
                } catch (err) {
                    console.log('No JSON data available');
                }
            }

            // 3. Try from plain text
            if (!type) {
                type = e.dataTransfer.getData('text/plain');
                console.log('Type from text data:', type);
            }

            if (!type) {
                console.error('No component type data received');
                return;
            }

            console.log('Handling drop for component type:', type);

            const rect = graphContainer.getBoundingClientRect();
            const dropPoint = {
                x: e.clientX - rect.left,
                y: e.clientY - rect.top
            };

            console.log('Drop point:', dropPoint);

            const canvasCoords = this.network.DOMtoCanvas({
                x: dropPoint.x,
                y: dropPoint.y
            });

            console.log('Canvas coordinates:', canvasCoords);

            if (!canvasCoords) {
                console.error('Failed to calculate drop position');
                return;
            }

            const id = `${type}-${Date.now()}`;
            const nodeData = {
                id: id,
                label: `${type}\n#${id}`,
                x: canvasCoords.x,
                y: canvasCoords.y,
                type: type,
                color: this.getNodeColor(type),
                physics: false
            };

            console.log('Adding node:', nodeData);

            try {
                this.nodes.add(nodeData);
                this.showSuccess('Component added successfully');
            } catch (error) {
                console.error('Error adding node:', error);
                this.showError('Failed to add component');
            } finally {
                // Clear the drag state
                dragState.currentType = null;
                dragState.isDragging = false;
            }
        };

        // Setup drag source event handlers
        console.log('Setting up event listeners for palette items...');
        paletteItems.forEach(item => {
            // Make both the palette-item and its node draggable
            item.setAttribute('draggable', 'true');
            item.style.cursor = 'grab';

            // Add the event listeners to the palette-item
            item.addEventListener('dragstart', handleDragStart);
            item.addEventListener('drag', handleDrag);
            item.addEventListener('dragend', handleDragEnd);

            // Also make the node element draggable
            const nodeElement = item.querySelector('.node');
            if (nodeElement) {
                nodeElement.setAttribute('draggable', 'true');
                nodeElement.style.cursor = 'grab';

                // Add the same event listeners to the node
                nodeElement.addEventListener('dragstart', handleDragStart);
                nodeElement.addEventListener('drag', handleDrag);
                nodeElement.addEventListener('dragend', handleDragEnd);
            }
        });

        // Setup drop target event handlers
        const dropHandlers = {
            dragenter: (e) => {
                e.preventDefault();
                if (dragState.isDragging) {
                    graphContainer.classList.add('drop-target');
                    console.log('Drag entered graph container');
                }
            },
            dragover: (e) => {
                e.preventDefault();
                if (dragState.isDragging) {
                    e.dataTransfer.dropEffect = 'copy';
                }
            },
            dragleave: (e) => {
                e.preventDefault();
                if (!e.currentTarget.contains(e.relatedTarget)) {
                    graphContainer.classList.remove('drop-target');
                    console.log('Drag left graph container');
                }
            },
            drop: handleDrop.bind(this)
        };

        // Add the event listeners
        Object.entries(dropHandlers).forEach(([event, handler]) => {
            graphContainer.addEventListener(event, handler);
        });

        // Add CSS styles dynamically
        const style = document.createElement('style');
        style.textContent = `
            .palette-item, .palette-item .node {
                user-select: none;
                -webkit-user-drag: element;
                cursor: grab;
            }
            .palette-item:active, .palette-item .node:active {
                cursor: grabbing;
            }
            .palette-item.dragging {
                opacity: 0.5;
                cursor: grabbing;
            }
            .workflow-graph.drop-target {
                background-color: rgba(0, 0, 0, 0.05);
            }
        `;
        document.head.appendChild(style);
    }

    setupCopyPaste() {
        // Add keyboard event listener
        document.addEventListener('keydown', async (e) => {
            // Check if the graph container is focused
            if (!this.container.contains(document.activeElement)) {
                return;
            }

            // Copy (Cmd+C on Mac, Ctrl+C on Windows)
            if ((e.metaKey || e.ctrlKey) && e.key === 'c') {
                const selectedNodes = this.network.getSelectedNodes();
                if (selectedNodes.length > 0) {
                    await this.copyComponents(selectedNodes);
                }
            }

            // Paste (Cmd+V on Mac, Ctrl+V on Windows)
            if ((e.metaKey || e.ctrlKey) && e.key === 'v') {
                if (this.clipboard.nodes.length > 0) {
                    await this.pasteComponents(e);
                }
            }
        });
    }

    setupSaveHandlers() {
        // Add handlers for Download and Preview buttons
        const downloadBtn = document.getElementById('download-btn');
        const previewBtn = document.getElementById('preview-btn');

        if (downloadBtn) {
            downloadBtn.classList.remove('btn-warning');
        }

        if (previewBtn) {
            previewBtn.classList.remove('btn-warning');
        }

        // Add warning when leaving page with unsaved changes
        window.addEventListener('beforeunload', (e) => {
            if (this.hasUnsavedChanges()) {
                e.preventDefault();
                e.returnValue = '';
            }
        });
    }

    setupFormHandlers() {
        // Workflow Metadata Form
        const workflowForm = document.getElementById('workflow-metadata-form');
        if (workflowForm) {
            workflowForm.addEventListener('change', (e) => {
                const formData = new FormData(workflowForm);
                this.unsavedChanges.workflow = {
                    name: formData.get('name'),
                    description: formData.get('description')
                };
                this.showUnsavedChanges();
            });
        }

        // Platform Configuration Form
        const platformForm = document.getElementById('platform-config-form');
        if (platformForm) {
            platformForm.addEventListener('change', (e) => {
                const formData = new FormData(platformForm);
                this.unsavedChanges.platform = {
                    name: formData.get('name'),
                    job_runner: formData.get('job_runner')
                };
                this.showUnsavedChanges();
            });
        }

        // Container Configuration Form
        const containerForm = document.getElementById('container-config-form');
        if (containerForm) {
            const imagePathInput = containerForm.querySelector('input[name="image_path"]');
            if (imagePathInput) {
                // Set default value if empty
                if (!imagePathInput.value) {
                    imagePathInput.value = 'cpu-emu.sif';
                }

                // Save initial value
                const formData = new FormData();
                formData.append('image_path', imagePathInput.value);
                fetch('/api/workflow/container', {
                    method: 'POST',
                    body: formData
                }).then(response => {
                    if (!response.ok) {
                        throw new Error('Failed to save container configuration');
                    }
                    return response.json();
                }).catch(error => {
                    console.error('Error saving container configuration:', error);
                    this.showError('Failed to save container configuration');
                });

                // Handle changes
                containerForm.addEventListener('change', (e) => {
                    const formData = new FormData(containerForm);
                    const imagePath = formData.get('image_path');

                    // Ensure we always have a value
                    this.unsavedChanges.container = {
                        image_path: imagePath || 'cpu-emu.sif'
                    };

                    // Save immediately
                    fetch('/api/workflow/container', {
                        method: 'POST',
                        body: formData
                    }).then(response => {
                        if (!response.ok) {
                            throw new Error('Failed to save container configuration');
                        }
                        return response.json();
                    }).then(() => {
                        this.showSuccess('Container configuration saved');
                    }).catch(error => {
                        console.error('Error saving container configuration:', error);
                        this.showError('Failed to save container configuration');
                    });

                    this.showUnsavedChanges();
                });
            }
        }
    }

    showUnsavedChanges() {
        // Show indicator that there are unsaved changes
        const downloadBtn = document.getElementById('download-btn');
        const previewBtn = document.getElementById('preview-btn');

        if (this.hasUnsavedChanges()) {
            if (downloadBtn) downloadBtn.classList.add('btn-warning');
            if (previewBtn) previewBtn.classList.add('btn-warning');

            // Show toast notification
            const toast = document.createElement('div');
            toast.className = 'toast';
            toast.textContent = 'Changes stored. Click Download or Preview to save.';
            document.body.appendChild(toast);
            setTimeout(() => toast.remove(), 2000);
        } else {
            if (downloadBtn) downloadBtn.classList.remove('btn-warning');
            if (previewBtn) previewBtn.classList.remove('btn-warning');
        }
    }

    hasUnsavedChanges() {
        return this.unsavedChanges.nodes.size > 0 ||
            this.unsavedChanges.edges.size > 0 ||
            this.unsavedChanges.workflow !== null ||
            this.unsavedChanges.platform !== null ||
            this.unsavedChanges.container !== null;
    }

    async saveAllChanges() {
        try {
            // Save workflow metadata if changed
            if (this.unsavedChanges.workflow) {
                const workflowFormData = new FormData();
                workflowFormData.append('workflow_name', this.unsavedChanges.workflow.name || '');
                workflowFormData.append('workflow_description', this.unsavedChanges.workflow.description || '');

                const workflowResponse = await fetch('/api/workflow/metadata', {
                    method: 'POST',
                    body: workflowFormData
                });

                if (!workflowResponse.ok) {
                    throw new Error('Failed to save workflow metadata');
                }
            }

            // Save platform configuration if changed
            if (this.unsavedChanges.platform) {
                const platformFormData = new FormData();
                platformFormData.append('platform_name', this.unsavedChanges.platform.name || '');
                platformFormData.append('job_runner', this.unsavedChanges.platform.job_runner || '');

                const platformResponse = await fetch('/api/workflow/platform', {
                    method: 'POST',
                    body: platformFormData
                });

                if (!platformResponse.ok) {
                    throw new Error('Failed to save platform configuration');
                }
            }

            // Save container configuration if changed
            if (this.unsavedChanges.container) {
                const containerFormData = new FormData();
                containerFormData.append('image_path', this.unsavedChanges.container.image_path || '');

                const containerResponse = await fetch('/api/workflow/container', {
                    method: 'POST',
                    body: containerFormData
                });

                if (!containerResponse.ok) {
                    throw new Error('Failed to save container configuration');
                }
            }

            // Save components
            for (const [id, config] of this.unsavedChanges.nodes.entries()) {
                try {
                    // Create the component data structure
                    const componentData = {
                        type: config.type,
                        resources: {
                            partition: config.resources && config.resources.partition || 'ifarm',
                            cpus_per_task: config.resources && parseInt(config.resources.cpus_per_task) || 4,
                            mem: config.resources && config.resources.mem || '8G'
                        }
                    };

                    // Add network configuration if present and not a sender
                    if (config.type !== 'sender' && config.network) {
                        if (config.network && config.network.listen_port) {
                            componentData.network = {
                                listen_port: parseInt(config.network.listen_port)
                            };
                            if (config.type === 'receiver' && config.network.bind_address) {
                                componentData.network.bind_address = config.network.bind_address;
                            }
                        }
                    }

                    // Add type-specific configuration
                    if (config.type === 'emulator' && config.configuration) {
                        componentData.configuration = {
                            threads: parseInt(config.configuration.threads) || 4,
                            latency: parseInt(config.configuration.latency) || 50,
                            mem_footprint: parseFloat(config.configuration.mem_footprint) || 0.05,
                            output_size: parseFloat(config.configuration.output_size) || 0.001
                        };
                    } else if (config.type === 'sender' && config.test_data) {
                        componentData.test_data = {
                            size: config.test_data.size || '100M'
                        };
                    }

                    // Send the component data
                    const response = await fetch(`/api/components/${id}`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(componentData)
                    });

                    if (!response.ok) {
                        throw new Error(`Failed to save component ${id}`);
                    }
                } catch (error) {
                    console.error(`Error saving component ${id}:`, error);
                    this.showError(`Failed to save component ${id}`);
                }
            }

            // Save edges
            for (const [id, config] of this.unsavedChanges.edges.entries()) {
                try {
                    const formData = new FormData();
                    formData.append('from_id', config.from);
                    formData.append('to_id', config.to);
                    formData.append('description', config.description || '');

                    const response = await fetch('/api/edges', {
                        method: 'POST',
                        body: formData
                    });

                    if (!response.ok) {
                        throw new Error(`Failed to save edge ${id}`);
                    }
                } catch (error) {
                    console.error(`Error saving edge ${id}:`, error);
                    this.showError(`Failed to save edge ${id}`);
                }
            }

            // Clear all unsaved changes
            this.unsavedChanges = {
                nodes: new Map(),
                edges: new Map(),
                workflow: null,
                platform: null,
                container: null
            };

            // Update UI
            const downloadBtn = document.getElementById('download-btn');
            const previewBtn = document.getElementById('preview-btn');
            if (downloadBtn) downloadBtn.classList.remove('btn-warning');
            if (previewBtn) previewBtn.classList.remove('btn-warning');

            // Show success message
            this.showSuccess('All changes saved successfully');

        } catch (error) {
            console.error('Error saving changes:', error);
            this.showError(`Failed to save changes: ${error.message}`);
        }
    }

    createComponentFormData(id, config) {
        const formData = new FormData();
        formData.append('id', id);
        formData.append('type', config.type);

        // Add resources configuration
        if (config.resources) {
            formData.append('partition', config.resources.partition);
            formData.append('cpus_per_task', config.resources.cpus_per_task);
            formData.append('mem', config.resources.mem);
        }

        // Add network configuration for all component types
        if (config.network) {
            if (config.network.listen_port) {
                formData.append('listen_port', config.network.listen_port);
            }
            if (config.type === 'receiver' && config.network.bind_address) {
                formData.append('bind_address', config.network.bind_address);
            }
        }

        // Add type-specific configuration
        if (config.type === 'emulator' && config.configuration) {
            formData.append('threads', config.configuration.threads || '4');
            formData.append('latency', config.configuration.latency || '50');
            formData.append('mem_footprint', config.configuration.mem_footprint || '0.05');
            formData.append('output_size', config.configuration.output_size || '0.001');
        } else if (config.type === 'sender' && config.test_data) {
            formData.append('data_size', config.test_data.size || '100M');
        }

        return formData;
    }

    async copyComponents(nodeIds) {
        try {
            // Get current configuration
            const response = await fetch('/api/workflow/config');
            const config = await response.json();

            // Get selected nodes and their positions
            const selectedNodes = nodeIds.map(id => {
                const component = config.components[id];
                if (!component) return null;

                const position = this.network.getPosition(id);
                return {
                    id: id,
                    type: component.type,
                    config: {
                        resources: component.config && component.config.resources,
                        network: component.config && component.config.network,
                        configuration: component.config && component.config.configuration,
                        test_data: component.config && component.config.test_data
                    },
                    position: position
                };
            }).filter(Boolean);

            // Get edges between selected nodes
            const selectedEdges = this.edges.get().filter(edge => {
                return nodeIds.includes(edge.from) && nodeIds.includes(edge.to);
            }).map(edge => ({
                ...edge,
                description: edge.label  // Store the edge description
            }));

            // Calculate reference point (center of selection)
            const centerX = selectedNodes.reduce((sum, node) => sum + node.position.x, 0) / selectedNodes.length;
            const centerY = selectedNodes.reduce((sum, node) => sum + node.position.y, 0) / selectedNodes.length;

            // Store in clipboard with relative positions from center
            this.clipboard = {
                nodes: selectedNodes.map(node => ({
                    ...node,
                    relativePos: {
                        x: node.position.x - centerX,
                        y: node.position.y - centerY
                    }
                })),
                edges: selectedEdges,
                offset: { x: 0, y: 0 }  // Reset offset on new copy
            };

            // Show feedback
            const toast = document.createElement('div');
            toast.className = 'toast';
            toast.textContent = `Copied ${selectedNodes.length} components and ${selectedEdges.length} connections`;
            document.body.appendChild(toast);
            setTimeout(() => toast.remove(), 2000);

        } catch (error) {
            console.error('Error copying components:', error);
            alert('Failed to copy components');
        }
    }

    async pasteComponents(event) {
        if (!this.clipboard.nodes.length) return;

        try {
            // Get the current mouse position in canvas coordinates
            const pointer = this.network.getViewPosition();
            const scale = this.network.getScale();

            // Calculate paste position based on pointer and viewport
            const pastePosition = {
                x: pointer.x + (this.clipboard.offset.x * scale),
                y: pointer.y + (this.clipboard.offset.y * scale)
            };

            // Create a mapping of old IDs to new IDs
            const idMapping = {};
            const addedNodes = [];
            const addedEdges = [];

            // Create new components
            for (const component of this.clipboard.nodes) {
                const newId = await this.getUniqueComponentId(component.type);
                idMapping[component.id] = newId;

                // Calculate new position relative to paste position
                const position = {
                    x: pastePosition.x + (component.relativePos.x * scale),
                    y: pastePosition.y + (component.relativePos.y * scale)
                };

                try {
                    // Add the node and store the result
                    const nodeData = await this.addComponent(component.type, position, component.config, newId, true);
                    if (nodeData) {
                        addedNodes.push(nodeData);
                    }
                } catch (error) {
                    console.error(`Failed to add component ${newId}:`, error);
                    continue;
                }
            }

            // Create new edges using the ID mapping
            for (const edge of this.clipboard.edges) {
                if (!idMapping[edge.from] || !idMapping[edge.to]) continue;

                const description = edge.description || edge.label || '';
                const newEdgeData = {
                    from: idMapping[edge.from],
                    to: idMapping[edge.to],
                    label: description,
                    id: `${idMapping[edge.from]}-${idMapping[edge.to]}`,
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
                    color: {
                        color: '#2B7CE9',
                        highlight: '#2B7CE9',
                        hover: '#2B7CE9'
                    },
                    physics: false
                };

                // Create the edge in the backend
                const formData = new FormData();
                formData.append('from_id', newEdgeData.from);
                formData.append('to_id', newEdgeData.to);
                formData.append('description', description);

                try {
                    const response = await fetch('/api/edges', {
                        method: 'POST',
                        body: formData
                    });

                    if (response.ok) {
                        const data = await response.json();
                        if (data.status === 'success') {
                            addedEdges.push(newEdgeData);
                            // Store the edge configuration locally
                            this.unsavedChanges.edges.set(newEdgeData.id, {
                                from: newEdgeData.from,
                                to: newEdgeData.to,
                                description: description
                            });
                        }
                    }
                } catch (error) {
                    console.error(`Failed to add edge ${newEdgeData.id}:`, error);
                    continue;
                }
            }

            if (addedNodes.length > 0) {
                // Disable physics before adding nodes
                this.network.setOptions({ physics: { enabled: false } });

                // Batch update the visual graph
                this.nodes.add(addedNodes);
                if (addedEdges.length > 0) {
                    this.edges.add(addedEdges);
                }

                // Update offset for next paste
                this.clipboard.offset.x += 50;
                this.clipboard.offset.y += 50;

                // Show feedback
                const toast = document.createElement('div');
                toast.className = 'toast';
                toast.textContent = `Pasted ${addedNodes.length} components and ${addedEdges.length} connections`;
                document.body.appendChild(toast);
                setTimeout(() => toast.remove(), 2000);

                // Force a network redraw but don't fit - maintain current view
                this.network.redraw();
            } else {
                throw new Error('No components were successfully pasted');
            }

        } catch (error) {
            console.error('Error pasting components:', error);
            alert('Failed to paste components');
        }
    }

    async getUniqueComponentId(type) {
        let counter = 1;
        let id;
        do {
            id = `${type}${counter}`;
            counter++;
        } while (this.nodes.get(id));
        return id;
    }

    async addComponent(type, position, config = {}, providedId = null, skipVisualUpdate = false) {
        try {
            if (!type || !position) {
                throw new Error('Invalid component parameters');
            }

            const id = providedId || await this.getUniqueComponentId(type);
            const nodeData = {
                id: id,
                label: `${type}\n#${id.split('-')[1]}`,
                x: position.x,
                y: position.y,
                type: type,
                color: this.getNodeColor(type),
                physics: false
            };

            // Create form data for the request
            const formData = new FormData();
            formData.append('id', id);
            formData.append('type', type);

            // Set default values
            formData.append('partition', 'ifarm');
            formData.append('cpus_per_task', '4');
            formData.append('mem', type === 'emulator' ? '16G' : '8G');

            // Add the node visually first
            this.nodes.add(nodeData);

            // Then make the API call
            const response = await fetch('/api/components', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                // If the API call fails, remove the node
                this.nodes.remove(id);
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || 'Failed to add component');
            }

            // Show configuration modal after successful addition
            if (!skipVisualUpdate) {
                await this.openComponentConfig(id);
            }

            return nodeData;
        } catch (error) {
            console.error('Error adding component:', error);
            this.showError(error.message || 'Failed to add component');
            throw error;
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

        // Create form data for immediate backend save
        const formData = new FormData();
        formData.append('from_id', edgeData.from);
        formData.append('to_id', edgeData.to);
        formData.append('description', description);

        // Save edge to backend immediately
        fetch('/api/edges', {
            method: 'POST',
            body: formData
        })
            .then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    // Store the edge configuration locally
                    const edgeId = `${edgeData.from}-${edgeData.to}`;
                    this.unsavedChanges.edges.set(edgeId, {
                        from: edgeData.from,
                        to: edgeData.to,
                        description: description
                    });

                    // Update the visual edge
                    edgeData.label = description;
                    callback(edgeData);

                    // Show feedback
                    const toast = document.createElement('div');
                    toast.className = 'toast';
                    toast.textContent = 'Edge added successfully';
                    document.body.appendChild(toast);
                    setTimeout(() => toast.remove(), 2000);
                } else {
                    alert(data.message || 'Failed to add edge');
                    callback(null);
                }
            })
            .catch(error => {
                console.error('Error adding edge:', error);
                alert('Failed to add edge. Please try again.');
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
                // Remove the specified edges from the graph
                this.edges.remove(edgeIds);
                callback(edgeData);

                // Show feedback
                const toast = document.createElement('div');
                toast.className = 'toast';
                toast.textContent = `Deleted ${edgeIds.length} connection(s)`;
                document.body.appendChild(toast);
                setTimeout(() => toast.remove(), 2000);
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

        // Find edges connected to these nodes
        const connectedEdges = this.edges.get().filter(edge =>
            nodeIds.includes(edge.from) || nodeIds.includes(edge.to)
        );
        const connectedEdgeIds = connectedEdges.map(edge => edge.id);

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
                // Remove only the directly connected edges from the graph
                if (connectedEdgeIds.length > 0) {
                    this.edges.remove(connectedEdgeIds);
                }

                // Remove the nodes from the graph
                this.nodes.remove(nodeIds);
                callback(nodeData);

                // Show feedback
                const toast = document.createElement('div');
                toast.className = 'toast';
                toast.textContent = `Deleted ${nodeIds.length} component(s) and ${connectedEdgeIds.length} connection(s)`;
                document.body.appendChild(toast);
                setTimeout(() => toast.remove(), 2000);
            })
            .catch(error => {
                console.error('Error deleting components:', error);
                alert('Failed to delete some components. Please try again.');
                callback(null);
            });
    }

    async openComponentConfig(nodeId) {
        try {
            if (!nodeId) {
                throw new Error('Invalid component ID');
            }

            // Get current configuration
            const response = await fetch('/api/workflow/config');
            if (!response.ok) {
                throw new Error('Failed to fetch component configuration');
            }

            const configData = await response.json();
            const config = this.unsavedChanges.nodes.get(nodeId) || configData.components[nodeId] || {};
            const node = this.nodes.get(nodeId);

            if (!node) {
                throw new Error('Component not found');
            }

            // Create modal if it doesn't exist
            let modal = document.getElementById('componentConfigModal');
            if (!modal) {
                modal = document.createElement('div');
                modal.id = 'componentConfigModal';
                modal.className = 'modal fade';
                document.body.appendChild(modal);
            }

            // Set modal content
            modal.innerHTML = `
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title">Configure ${node.type}</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body">
                            <form id="componentConfigForm">
                                <!-- Form content will be added here -->
                            </form>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                            <button type="button" class="btn btn-primary" onclick="document.getElementById('componentConfigForm').dispatchEvent(new Event('submit'))">Apply</button>
                        </div>
                    </div>
                </div>`;

            // Get the form element
            const form = modal.querySelector('#componentConfigForm');
            let formHtml = '';

            // Add resources section
            formHtml += `
                <!-- Resources Section -->
                <div class="mb-3">
                    <h5>Resources</h5>
                    <div class="mb-2">
                        <label class="form-label">Partition</label>
                        <input type="text" class="form-control" name="partition" 
                            value="${config.resources ? config.resources.partition : 'ifarm'}" required>
                    </div>
                    <div class="mb-2">
                        <label class="form-label">CPUs per Task</label>
                        <input type="number" class="form-control" name="cpus_per_task" min="1" 
                            value="${config.resources ? config.resources.cpus_per_task : '4'}" required>
                    </div>
                    <div class="mb-2">
                        <label class="form-label">Memory</label>
                        <input type="text" class="form-control" name="mem" 
                            value="${config.resources ? config.resources.mem : '8G'}" required>
                    </div>
                </div>`;

            // Add network section only for emulator and receiver
            if (node.type === 'emulator' || node.type === 'receiver') {
                formHtml += `
                    <!-- Network Section -->
                    <div class="mb-3">
                        <h5>Network</h5>
                        <div class="mb-2">
                            <label class="form-label">Listen Port</label>
                            <input type="number" class="form-control" name="listen_port" min="1024" max="65535" 
                                value="${config.network ? config.network.listen_port : ''}">
                        </div>`;

                // Add bind address only for receiver
                if (node.type === 'receiver') {
                    formHtml += `
                        <div class="mb-2">
                            <label class="form-label">Bind Address</label>
                            <input type="text" class="form-control" name="bind_address" 
                                value="${config.network ? config.network.bind_address : '0.0.0.0'}">
                        </div>`;
                }

                formHtml += `</div>`;  // Close network section
            }

            // Add type-specific configuration
            if (node.type === 'emulator') {
                const emulatorConfig = config.configuration || {
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
                            <input type="number" class="form-control" name="threads" min="1" 
                                value="${emulatorConfig.threads}">
                        </div>
                        <div class="mb-2">
                            <label class="form-label">Latency (ms)</label>
                            <input type="number" class="form-control" name="latency" min="0" 
                                value="${emulatorConfig.latency}">
                        </div>
                        <div class="mb-2">
                            <label class="form-label">Memory Footprint</label>
                            <input type="number" class="form-control" name="mem_footprint" min="0" step="0.01" 
                                value="${emulatorConfig.mem_footprint}">
                        </div>
                        <div class="mb-2">
                            <label class="form-label">Output Size</label>
                            <input type="number" class="form-control" name="output_size" min="0" step="0.001" 
                                value="${emulatorConfig.output_size}">
                        </div>
                    </div>`;
            } else if (node.type === 'sender') {
                formHtml += `
                    <!-- Test Data Configuration -->
                    <div class="mb-3">
                        <h5>Test Data</h5>
                        <div class="mb-2">
                            <label class="form-label">Data Size</label>
                            <input type="text" class="form-control" name="data_size" 
                                value="${config.test_data ? config.test_data.size : '100M'}">
                        </div>
                    </div>`;
            }

            form.innerHTML = formHtml;

            // Modify form submit handler to store changes locally
            form.addEventListener('submit', async (e) => {
                e.preventDefault();
                const formData = new FormData(form);

                // Create base component config
                const componentConfig = {
                    type: node.type,
                    resources: {
                        partition: formData.get('partition'),
                        cpus_per_task: parseInt(formData.get('cpus_per_task')),
                        mem: formData.get('mem')
                    }
                };

                // Add network configuration only for emulator and receiver
                if (node.type === 'emulator' || node.type === 'receiver') {
                    const listenPort = formData.get('listen_port');
                    if (listenPort && listenPort.trim() !== '') {
                        componentConfig.network = {
                            listen_port: parseInt(listenPort)
                        };
                        if (node.type === 'receiver') {
                            const bindAddress = formData.get('bind_address');
                            if (bindAddress && bindAddress.trim() !== '') {
                                componentConfig.network.bind_address = bindAddress;
                            }
                        }
                    }
                }

                // Add type-specific configuration
                if (node.type === 'emulator') {
                    componentConfig.configuration = {
                        threads: parseInt(formData.get('threads')),
                        latency: parseInt(formData.get('latency')),
                        mem_footprint: parseFloat(formData.get('mem_footprint')),
                        output_size: parseFloat(formData.get('output_size'))
                    };
                } else if (node.type === 'sender') {
                    const dataSize = formData.get('data_size');
                    if (dataSize && dataSize.trim() !== '') {
                        componentConfig.test_data = {
                            size: dataSize
                        };
                    }
                }

                try {
                    // Send to backend immediately
                    const response = await fetch(`/api/components/${nodeId}`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(componentConfig)
                    });

                    if (!response.ok) {
                        throw new Error('Failed to save component configuration');
                    }

                    const data = await response.json();
                    if (data.status !== 'success') {
                        throw new Error(data.message || 'Failed to save component configuration');
                    }

                    // Store the changes locally after successful backend save
                    this.unsavedChanges.nodes.set(nodeId, componentConfig);

                    // Close the modal
                    bsModal.hide();

                    // Show feedback
                    const toast = document.createElement('div');
                    toast.className = 'toast';
                    toast.textContent = 'Changes saved successfully';
                    document.body.appendChild(toast);
                    setTimeout(() => toast.remove(), 2000);
                } catch (error) {
                    console.error('Error saving component:', error);
                    alert('Failed to save component configuration');
                }
            });

            // Show the modal
            const bsModal = new bootstrap.Modal(modal);
            bsModal.show();

            return new Promise((resolve, reject) => {
                modal.addEventListener('hidden.bs.modal', () => {
                    resolve();
                });

                modal.addEventListener('show.bs.modal.failed', () => {
                    reject(new Error('Failed to show configuration modal'));
                });
            });
        } catch (error) {
            console.error('Error opening component configuration:', error);
            this.showError(error.message || 'Failed to open component configuration');
            throw error;
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
            'sender': '#90EE90',
            'emulator': '#ADD8E6',
            'receiver': '#F08080'
        };
        return colors[type] || '#CCCCCC';
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
            const number = id.split('-')[1];
            const nodeData = {
                id: id,
                label: `${comp.type}\n#${number}`,
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

        // Add edges with their descriptions
        if (config.edges && Array.isArray(config.edges)) {
            config.edges.forEach(edge => {
                this.edges.add({
                    from: edge.from,
                    to: edge.to,
                    label: edge.description || '',
                    id: `${edge.from}-${edge.to}`,
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
                    color: {
                        color: '#2B7CE9',
                        highlight: '#2B7CE9',
                        hover: '#2B7CE9'
                    },
                    physics: false  // Disable physics for edges too
                });
            });
        }

        // No need to stabilize or fix/unfix nodes since physics is disabled
    }

    async loadWorkflowState() {
        try {
            const response = await fetch('/api/workflow/config');
            if (!response.ok) {
                throw new Error('Failed to load workflow configuration');
            }

            const config = await response.json();

            // Initialize empty configuration if none exists
            if (!config) {
                config = {
                    workflow: {
                        name: '',
                        description: ''
                    },
                    platform: {
                        name: '',
                        job_runner: ''
                    },
                    containers: {
                        image_path: 'cpu-emu.sif'
                    },
                    components: {},
                    edges: []
                };
            }

            // Clear existing nodes and edges
            this.nodes.clear();
            this.edges.clear();
            this.unsavedChanges.edges.clear();  // Clear unsaved edge changes

            // Add components as nodes
            for (const [id, component] of Object.entries(config.components || {})) {
                const number = id.split('-')[1];
                this.nodes.add({
                    id: id,
                    label: `${component.type}\n#${number}`,
                    type: component.type,
                    color: this.getNodeColor(component.type),
                    physics: false
                });
            }

            // Add edges with their descriptions and styling
            if (config.edges && Array.isArray(config.edges)) {
                for (const edge of config.edges) {
                    const edgeId = `${edge.from}-${edge.to}`;
                    const edgeData = {
                        from: edge.from,
                        to: edge.to,
                        label: edge.description || '',
                        id: edgeId,
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
                        color: {
                            color: '#2B7CE9',
                            highlight: '#2B7CE9',
                            hover: '#2B7CE9'
                        },
                        physics: false
                    };

                    // Add edge to the visual graph
                    this.edges.add(edgeData);

                    // Store edge configuration in unsavedChanges
                    this.unsavedChanges.edges.set(edgeId, {
                        from: edge.from,
                        to: edge.to,
                        description: edge.description || ''
                    });
                }
            }

            // Disable physics after loading
            this.network.setOptions({ physics: { enabled: false } });

            // Apply automatic layout and stabilize
            this.network.stabilize();
        } catch (error) {
            console.error('Error loading workflow state:', error);
            this.showError('Failed to load workflow state');
        }
    }

    showError(message) {
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.style.backgroundColor = '#dc3545';
        toast.style.color = 'white';
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), 2000);
    }

    showSuccess(message) {
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), 2000);
    }
}

// Component type definitions and styling
const componentTypes = {
    sender: {
        color: '#a8e6cf',
        label: 'Sender',
        shape: 'box'
    },
    load_balancer: {
        color: '#ffadad',
        label: 'Load Balancer',
        shape: 'diamond'
    },
    emulator: {
        color: '#bde0fe',
        label: 'Emulator',
        shape: 'box'
    },
    aggregator: {
        color: '#e4c1f9',
        label: 'Aggregator',
        shape: 'hexagon'
    },
    receiver: {
        color: '#ffd3b6',
        label: 'Receiver',
        shape: 'box'
    }
};

let componentCounter = 0;
let workflowGraph;

// Replace the initialization code at the bottom of the file with this:
document.addEventListener('DOMContentLoaded', () => {
    console.log('DOM Content Loaded');
    try {
        const container = document.getElementById('workflow-graph');
        console.log('Looking for workflow-graph container:', container);

        if (!container) {
            throw new Error('Workflow graph container not found');
        }

        // Check if vis is loaded
        if (typeof vis === 'undefined') {
            console.error('vis library not loaded');
            const scripts = document.querySelectorAll('script');
            console.log('Loaded scripts:', Array.from(scripts).map(s => s.src));
            throw new Error('vis library not loaded');
        }

        // Initialize the graph
        try {
            console.log('Attempting to create WorkflowGraph');
            window.workflowGraph = new WorkflowGraph(container);
            console.log('WorkflowGraph created successfully');
        } catch (error) {
            console.error('Failed to create WorkflowGraph:', error);
            const errorDiv = document.createElement('div');
            errorDiv.className = 'alert alert-danger';
            errorDiv.textContent = `Failed to initialize workflow graph: ${error.message}`;
            container.parentNode.insertBefore(errorDiv, container);
        }
    } catch (error) {
        console.error('Error during initialization:', error);
        // Show error on page
        const errorDiv = document.createElement('div');
        errorDiv.className = 'alert alert-danger';
        errorDiv.style.margin = '20px';
        errorDiv.textContent = `Initialization error: ${error.message}`;
        document.body.insertBefore(errorDiv, document.body.firstChild);
    }
});

// Add a window error handler to catch any script loading errors
window.addEventListener('error', (event) => {
    console.error('Script error:', event.error);
    if (event.filename) {
        console.error('In file:', event.filename);
    }
}); 