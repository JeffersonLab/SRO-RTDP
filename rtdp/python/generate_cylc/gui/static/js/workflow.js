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

        // Define valid edge rules based on Cylc workflow
        this.edgeRules = {
            'receiver': {
                'emulator': [':ready'],
            },
            'emulator': {
                'sender': [':ready'],
            },
            'sender': {
                'receiver': [':succeeded'],
            }
        };

        // Track available numbers for each component type
        this.componentCounters = {
            sender: 0,
            receiver: 0,
            emulator: 0
        };

        // Track deleted numbers for reuse
        this.deletedNumbers = {
            sender: new Set(),
            receiver: new Set(),
            emulator: new Set()
        };

        if (!this.graphContainer) {
            throw new Error('Graph container not found');
        }

        this.defaultConfigs = {
            sender: {
                ntasks: 1,
                cpus_per_task: 4,
                mem: "8G",
                partition: "ifarm",
                timeout: "2h"
            },
            receiver: {
                ntasks: 1,
                cpus_per_task: 4,
                mem: "8G",
                partition: "ifarm",
                timeout: "2h",
                port: 50080
            },
            emulator: {
                ntasks: 1,
                cpus_per_task: 4,
                mem: "16G",
                partition: "ifarm",
                timeout: "2h",
                port: 50888,
                threads: 4,
                latency: 50,
                mem_footprint: 0.05,
                output_size: 0.001
            }
        };

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
                    shadow: true,
                    // Customize selection style
                    chosen: {
                        node: function (values, id, selected, hovering) {
                            if (selected) {
                                values.borderWidth = 3;
                                values.borderColor = '#5D9CEC';
                                values.shadowColor = 'rgba(93, 156, 236, 0.3)';
                                values.shadowSize = 10;
                            }
                        }
                    }
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
                    },
                    color: {
                        color: '#2B7CE9',
                        highlight: '#2B7CE9',
                        hover: '#2B7CE9'
                    }
                },
                interaction: {
                    dragNodes: true,
                    dragView: true,
                    zoomView: true,
                    hover: true,
                    selectable: true,
                    selectConnectedEdges: false,  // Disable edge selection highlighting
                    multiselect: true,  // Re-enable multiselect for box selection
                    keyboard: {
                        enabled: true,
                        bindToWindow: true
                    }
                },
                manipulation: {
                    enabled: true,
                    addEdge: (edgeData, callback) => {
                        this.handleEdgeCreation(edgeData, callback);
                    },
                    deleteEdge: (edgeData, callback) => {
                        callback(edgeData);
                    }
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

            // Add selection handling
            let isSelecting = false;
            let selectionRect = null;
            let isDraggingSelection = false;
            let dragStartPos = null;
            let selectedNodesInitialPos = {};

            // Create context menu
            const contextMenu = document.createElement('div');
            contextMenu.className = 'context-menu';
            contextMenu.style.display = 'none';
            contextMenu.style.position = 'absolute';
            contextMenu.style.zIndex = '1000';
            contextMenu.style.backgroundColor = 'white';
            contextMenu.style.border = '1px solid #ccc';
            contextMenu.style.borderRadius = '4px';
            contextMenu.style.padding = '5px 0';
            contextMenu.style.boxShadow = '0 2px 5px rgba(0,0,0,0.2)';
            contextMenu.innerHTML = `
                <div class="context-menu-item" data-action="delete" style="padding: 8px 12px; cursor: pointer; hover:background-color: #f0f0f0;">
                    Delete Selected
                </div>
            `;
            document.body.appendChild(contextMenu);

            // Hide context menu on click outside
            document.addEventListener('click', () => {
                contextMenu.style.display = 'none';
            });

            // Handle delete key press for selected nodes
            document.addEventListener('keydown', (event) => {
                if (event.key === 'Delete') {
                    const selectedNodes = this.network.getSelectedNodes();
                    // Only proceed if we have actual nodes selected
                    if (selectedNodes && Array.isArray(selectedNodes) && selectedNodes.length > 0) {
                        // Verify that all selected nodes exist in the network
                        const validNodes = selectedNodes.filter(nodeId =>
                            this.network.body.nodes[nodeId] && this.components[nodeId]
                        );

                        if (validNodes.length > 0) {
                            const nodeList = validNodes.join(', ');
                            if (confirm(`Are you sure you want to delete the following components?\n${nodeList}`)) {
                                validNodes.forEach(nodeId => {
                                    this.deleteComponent(nodeId, false);
                                });
                            }
                        }
                    }
                }
            });

            // Context menu item click handler
            contextMenu.addEventListener('click', (e) => {
                const action = e.target.getAttribute('data-action');
                if (action === 'delete') {
                    const selectedNodes = this.network.getSelectedNodes();
                    // Only proceed if we have actual nodes selected
                    if (selectedNodes && Array.isArray(selectedNodes) && selectedNodes.length > 0) {
                        // Verify that all selected nodes exist in the network
                        const validNodes = selectedNodes.filter(nodeId =>
                            this.network.body.nodes[nodeId] && this.components[nodeId]
                        );

                        if (validNodes.length > 0) {
                            if (confirm(`Are you sure you want to delete ${validNodes.length} selected component(s)?`)) {
                                validNodes.forEach(nodeId => {
                                    this.deleteComponent(nodeId, false);
                                });
                            }
                        }
                    }
                }
                contextMenu.style.display = 'none';
            });

            // Create selection rectangle element
            const createSelectionRect = () => {
                const rect = document.createElement('div');
                rect.style.position = 'absolute';
                rect.style.border = '1px solid #5D9CEC';
                rect.style.backgroundColor = 'rgba(93, 156, 236, 0.1)';
                rect.style.pointerEvents = 'none';
                return rect;
            };

            // Mouse down event - start selection or dragging
            this.graphContainer.addEventListener('mousedown', (e) => {
                // Hide context menu on any mouse down
                contextMenu.style.display = 'none';

                const selectedNodes = this.network.getSelectedNodes() || [];
                const clickedNode = this.network.getNodeAt({
                    x: e.clientX - this.graphContainer.getBoundingClientRect().left,
                    y: e.clientY - this.graphContainer.getBoundingClientRect().top
                });

                if (e.button === 0) { // Left click
                    if (selectedNodes.length > 0 && clickedNode && selectedNodes.includes(clickedNode)) {
                        // Start dragging selection
                        isDraggingSelection = true;
                        dragStartPos = { x: e.clientX, y: e.clientY };

                        // Store initial positions of all selected nodes
                        selectedNodes.forEach(nodeId => {
                            const nodePos = this.network.getPositions([nodeId])[nodeId];
                            selectedNodesInitialPos[nodeId] = { ...nodePos };
                        });
                    } else if (e.shiftKey) { // Only start box selection when Shift is held
                        // Start box selection
                        isSelecting = true;
                        const rect = this.graphContainer.getBoundingClientRect();
                        const startX = e.clientX - rect.left;
                        const startY = e.clientY - rect.top;

                        selectionRect = createSelectionRect();
                        selectionRect.style.left = startX + 'px';
                        selectionRect.style.top = startY + 'px';
                        this.graphContainer.appendChild(selectionRect);

                        selectionRect.startX = startX;
                        selectionRect.startY = startY;
                    } else if (!clickedNode) {
                        // Clear selection when clicking on empty space without shift
                        this.network.unselectAll();
                    }
                } else if (e.button === 2) { // Right click
                    e.preventDefault();
                    e.stopPropagation();

                    // Get valid selected nodes before any new selection
                    const validSelectedNodes = (this.network.getSelectedNodes() || []).filter(nodeId => {
                        const node = this.network.body.nodes[nodeId];
                        const component = this.components[nodeId];
                        return node && component && node.selected;
                    });

                    // Only proceed if we clicked on a node or already have valid selections
                    if (clickedNode && this.network.body.nodes[clickedNode] && this.components[clickedNode]) {
                        // If clicked on a node that's not selected, select only this node
                        if (!validSelectedNodes.includes(clickedNode)) {
                            this.network.selectNodes([clickedNode]);
                        }
                        // Show context menu
                        contextMenu.style.display = 'block';
                        contextMenu.style.left = e.pageX + 'px';
                        contextMenu.style.top = e.pageY + 'px';
                    } else if (validSelectedNodes.length > 0 && clickedNode) {
                        // Show context menu only if we have existing valid selections and clicked on a node
                        contextMenu.style.display = 'block';
                        contextMenu.style.left = e.pageX + 'px';
                        contextMenu.style.top = e.pageY + 'px';
                    }
                }
            });

            // Mouse move event - update selection or move selected nodes
            this.graphContainer.addEventListener('mousemove', (e) => {
                if (isSelecting && selectionRect && e.shiftKey) {
                    // Box selection logic
                    const rect = this.graphContainer.getBoundingClientRect();
                    const currentX = e.clientX - rect.left;
                    const currentY = e.clientY - rect.top;

                    const left = Math.min(selectionRect.startX, currentX);
                    const top = Math.min(selectionRect.startY, currentY);
                    const width = Math.abs(currentX - selectionRect.startX);
                    const height = Math.abs(currentY - selectionRect.startY);

                    selectionRect.style.left = left + 'px';
                    selectionRect.style.top = top + 'px';
                    selectionRect.style.width = width + 'px';
                    selectionRect.style.height = height + 'px';

                    // Select nodes within the rectangle
                    const selectedNodes = [];
                    const allNodes = this.network.body.nodes;

                    Object.values(allNodes).forEach(node => {
                        const nodePos = this.network.canvasToDOM({
                            x: node.x,
                            y: node.y
                        });

                        if (nodePos.x >= left && nodePos.x <= left + width &&
                            nodePos.y >= top && nodePos.y <= top + height) {
                            selectedNodes.push(node.id);
                        }
                    });

                    this.network.selectNodes(selectedNodes);
                } else if (isDraggingSelection) {
                    // Move selected nodes
                    const dx = e.clientX - dragStartPos.x;
                    const dy = e.clientY - dragStartPos.y;
                    const scale = this.network.getScale();

                    Object.entries(selectedNodesInitialPos).forEach(([nodeId, initialPos]) => {
                        this.network.moveNode(nodeId,
                            initialPos.x + dx / scale,
                            initialPos.y + dy / scale
                        );
                    });
                }
            });

            // Mouse up event - end selection or dragging
            this.graphContainer.addEventListener('mouseup', (e) => {
                if (isSelecting) {
                    isSelecting = false;
                    if (selectionRect && selectionRect.parentNode) {
                        selectionRect.parentNode.removeChild(selectionRect);
                    }
                    selectionRect = null;
                }
                if (isDraggingSelection) {
                    isDraggingSelection = false;
                    dragStartPos = null;
                    selectedNodesInitialPos = {};
                }
            });

            // Prevent default context menu
            this.graphContainer.addEventListener('contextmenu', (e) => {
                e.preventDefault();
            });

            // Handle double-click for configuration
            this.network.on('doubleClick', (params) => {
                if (params.nodes.length > 0) {
                    this.openComponentConfig(params.nodes[0]);
                }
            });

            // Handle delete events from vis.js
            this.network.on('deleteNode', (params) => {
                if (params.nodes && params.nodes.length > 0) {
                    params.nodes.forEach(nodeId => {
                        this.deleteComponent(nodeId);
                    });
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
        let number;
        // Check if there are any deleted numbers available for reuse
        if (this.deletedNumbers[type].size > 0) {
            // Get the smallest deleted number
            number = Math.min(...this.deletedNumbers[type]);
            // Remove it from the deleted set
            this.deletedNumbers[type].delete(number);
        } else {
            // If no deleted numbers available, increment counter
            this.componentCounters[type]++;
            number = this.componentCounters[type];
        }

        const id = `${type}-${number}`;

        const nodeData = {
            id: id,
            label: `${type}\n#${number}`,
            x: position.x,
            y: position.y,
            fixed: {
                x: true,
                y: true
            },
            color: this.getNodeColor(type)
        };

        // Add the node to the network with default configuration
        this.network.body.data.nodes.add(nodeData);
        this.components[id] = {
            type,
            config: { ...this.defaultConfigs[type] }  // Clone default config
        };

        // Make API call to add component
        fetch('/api/components', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                name: id,
                type: type,
                config: this.defaultConfigs[type]  // Include default config in API call
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
                // Add the number back to deleted numbers for reuse
                this.deletedNumbers[type].add(number);
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
        try {
            console.log('Opening config for component:', id);

            // Get the modal element
            const modalElement = document.getElementById('componentModal');
            if (!modalElement) {
                throw new Error('Modal element not found');
            }

            // Get the form elements
            const form = document.getElementById('componentFields');
            const componentIdInput = document.getElementById('componentId');

            if (!form || !componentIdInput) {
                throw new Error('Form elements not found');
            }

            componentIdInput.value = id;

            // Get the component data
            const component = this.components[id];
            if (!component) {
                throw new Error('Component not found: ' + id);
            }

            // Use component's config or default if not set
            const config = component.config && Object.keys(component.config).length > 0
                ? component.config
                : this.defaultConfigs[component.type];

            console.log('Component config:', config);

            // Group settings by category
            const categories = {
                'Resource Settings': ['ntasks', 'cpus_per_task', 'mem', 'partition', 'timeout'],
                'Network Settings': ['port'],
                'Emulator Settings': ['threads', 'latency', 'mem_footprint', 'output_size']
            };

            // Generate form with categorized sections
            let formContent = '';
            for (const [category, fields] of Object.entries(categories)) {
                const relevantFields = fields.filter(field => field in config);
                if (relevantFields.length > 0) {
                    formContent += `
                        <div class="card mb-3">
                            <div class="card-header bg-light">
                                <h6 class="mb-0">${category}</h6>
                            </div>
                            <div class="card-body">
                    `;

                    for (const field of relevantFields) {
                        const value = config[field];
                        const isNumber = typeof value === 'number';
                        formContent += `
                            <div class="mb-3">
                                <label for="${field}" class="form-label fw-bold">
                                    ${this.formatFieldLabel(field)}
                                </label>
                                <input type="${isNumber ? 'number' : 'text'}" 
                                       class="form-control" 
                                       id="${field}"
                                       name="${field}" 
                                       value="${value}"
                                       ${isNumber ? 'step="any"' : ''}
                                       required>
                                <div class="form-text text-muted">
                                    ${this.getFieldDescription(field)}
                                </div>
                            </div>
                        `;
                    }

                    formContent += `
                            </div>
                        </div>
                    `;
                }
            }

            console.log('Generated form content:', formContent);

            // Update the modal title
            const modalTitle = modalElement.querySelector('.modal-title');
            if (modalTitle) {
                modalTitle.textContent = `Configure ${component.type} #${id}`;
            }

            // Set the form content
            form.innerHTML = formContent;

            // Create new modal instance
            const bsModal = new bootstrap.Modal(modalElement, {
                backdrop: 'static',
                keyboard: false
            });

            // Show the modal
            bsModal.show();

        } catch (error) {
            console.error('Error opening component config:', error);
            alert('Error opening component configuration: ' + error.message);
        }
    }

    formatFieldLabel(field) {
        // Convert field name to title case with spaces
        return field
            .split('_')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    }

    getFieldDescription(field) {
        // Provide helpful descriptions for fields
        const descriptions = {
            ntasks: 'Number of tasks for this component',
            cpus_per_task: 'Number of CPUs per task',
            mem: 'Memory allocation (e.g., 8G, 16G)',
            partition: 'Slurm partition name',
            timeout: 'Job timeout duration (e.g., 2h)',
            port: 'Network port for communication',
            threads: 'Number of processing threads',
            latency: 'Processing latency per GB of data',
            mem_footprint: 'Memory footprint in GB',
            output_size: 'Output size in GB'
        };
        return descriptions[field] || field;
    }

    saveComponentConfig() {
        try {
            const componentId = document.getElementById('componentId').value;
            const form = document.getElementById('componentForm');
            const formData = new FormData(form);
            const config = {};

            formData.forEach((value, key) => {
                if (key !== 'componentId') {
                    // Convert numeric values
                    config[key] = !isNaN(value) && value !== '' ? Number(value) : value;
                }
            });

            // Update local state
            if (this.components[componentId]) {
                this.components[componentId].config = config;
            }

            // Make API call to update component config
            fetch(`/api/component-config/${componentId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(config)
            })
                .then(response => response.json())
                .then(data => {
                    if (data.status === 'success') {
                        // Close the modal
                        const modal = bootstrap.Modal.getInstance(document.getElementById('componentModal'));
                        if (modal) {
                            modal.hide();
                        }

                        // Show success message
                        this.showMessage('Configuration saved successfully', 'success');
                    } else {
                        throw new Error('Failed to save configuration');
                    }
                })
                .catch(error => {
                    console.error('Error saving component config:', error);
                    this.showMessage('Failed to save configuration', 'error');
                });
        } catch (error) {
            console.error('Error in saveComponentConfig:', error);
            this.showMessage('Error saving configuration', 'error');
        }
    }

    showMessage(message, type) {
        const messagesDiv = document.getElementById('messages');
        if (messagesDiv) {
            messagesDiv.innerHTML = `<div class="${type}-message">${message}</div>`;
            setTimeout(() => {
                messagesDiv.innerHTML = '';
            }, 3000);
        }
    }

    deleteComponent(nodeId, showConfirmation = true) {
        if (!showConfirmation || confirm(`Are you sure you want to delete ${nodeId}?`)) {
            // Remove from network
            this.network.body.data.nodes.remove(nodeId);

            // Get component type and number for reuse
            const component = this.components[nodeId];
            if (component) {
                // Extract the number from the nodeId (e.g., "sender-2" -> 2)
                const number = parseInt(nodeId.split('-')[1]);
                if (!isNaN(number)) {
                    // Add the number to the deleted set for reuse
                    this.deletedNumbers[component.type].add(number);
                }

                // Make API call to delete component
                fetch(`/api/components/${nodeId}`, {
                    method: 'DELETE'
                })
                    .then(response => response.json())
                    .then(data => {
                        if (data.status === 'success') {
                            // Remove from local state
                            delete this.components[nodeId];
                            this.showMessage(`Component ${nodeId} deleted successfully`, 'success');
                        }
                    })
                    .catch(error => {
                        console.error('Error deleting component:', error);
                        this.showMessage('Failed to delete component', 'error');
                    });
            }
        }
    }

    handleEdgeCreation(edgeData, callback) {
        try {
            const fromNode = this.components[edgeData.from];
            const toNode = this.components[edgeData.to];

            if (!fromNode || !toNode) {
                throw new Error('Invalid nodes');
            }

            // Check if this connection is allowed by the rules
            const allowedConditions = this.edgeRules[fromNode.type]?.[toNode.type];
            if (!allowedConditions) {
                throw new Error(`Invalid connection: ${fromNode.type} cannot connect to ${toNode.type}`);
            }

            // If there's only one condition, use it automatically
            if (allowedConditions.length === 1) {
                edgeData.label = allowedConditions[0];
                callback(edgeData);
            } else {
                // Show condition selection dialog
                this.showEdgeConditionDialog(allowedConditions, (selectedCondition) => {
                    if (selectedCondition) {
                        edgeData.label = selectedCondition;
                        callback(edgeData);
                    } else {
                        callback(null); // Cancel edge creation
                    }
                });
            }

            // Add to connections array
            this.connections.push({
                from: edgeData.from,
                to: edgeData.to,
                condition: edgeData.label
            });

        } catch (error) {
            console.error('Edge creation error:', error);
            alert(error.message);
            callback(null); // Cancel edge creation
        }
    }

    showEdgeConditionDialog(conditions, callback) {
        // Create modal for condition selection
        const modal = document.createElement('div');
        modal.className = 'modal fade';
        modal.innerHTML = `
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">Select Edge Condition</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <select class="form-select" id="conditionSelect">
                            ${conditions.map(condition =>
            `<option value="${condition}">${condition}</option>`
        ).join('')}
                        </select>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                        <button type="button" class="btn btn-primary" id="confirmCondition">Confirm</button>
                    </div>
                </div>
            </div>
        `;

        document.body.appendChild(modal);
        const modalInstance = new bootstrap.Modal(modal);

        // Handle condition selection
        const confirmBtn = modal.querySelector('#confirmCondition');
        confirmBtn.addEventListener('click', () => {
            const select = modal.querySelector('#conditionSelect');
            const selectedCondition = select.value;
            modalInstance.hide();
            callback(selectedCondition);
            setTimeout(() => modal.remove(), 500);
        });

        modal.addEventListener('hidden.bs.modal', () => {
            callback(null);
            setTimeout(() => modal.remove(), 500);
        });

        modalInstance.show();
    }
}

// Initialize when the page loads
document.addEventListener('DOMContentLoaded', () => {
    if (typeof vis !== 'undefined') {
        window.workflowGraph = new WorkflowGraph();
    }
}); 