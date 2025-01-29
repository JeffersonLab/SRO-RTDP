// Using D3.js for graph visualization
class WorkflowGraph {
    constructor() {
        this.svg = d3.select('#workflow-graph')
            .append('svg')
            .attr('width', '100%')
            .attr('height', '500px');

        this.simulation = d3.forceSimulation()
            .force('link', d3.forceLink().id(d => d.id))
            .force('charge', d3.forceManyBody().strength(-300))
            .force('center', d3.forceCenter(
                this.svg.node().getBoundingClientRect().width / 2,
                250
            ));

        this.nodes = [];
        this.links = [];

        this.initializeGraph();
    }

    initializeGraph() {
        // Create arrow marker for directed edges
        this.svg.append('defs').append('marker')
            .attr('id', 'arrowhead')
            .attr('viewBox', '-0 -5 10 10')
            .attr('refX', 20)
            .attr('refY', 0)
            .attr('orient', 'auto')
            .attr('markerWidth', 6)
            .attr('markerHeight', 6)
            .attr('xoverflow', 'visible')
            .append('svg:path')
            .attr('d', 'M 0,-5 L 10 ,0 L 0,5')
            .attr('fill', '#999')
            .style('stroke', 'none');
    }

    updateGraph(components, edges) {
        // Convert components and edges to D3 format
        this.nodes = Object.entries(components).map(([id, comp]) => ({
            id,
            type: comp.type
        }));

        this.links = edges.map(edge => ({
            source: edge.from,
            target: edge.to,
            type: edge.type
        }));

        // Update force simulation
        this.simulation
            .nodes(this.nodes)
            .force('link').links(this.links);

        // Draw links
        const link = this.svg.selectAll('.link')
            .data(this.links)
            .join('line')
            .attr('class', 'link')
            .attr('stroke', '#999')
            .attr('stroke-width', 2)
            .attr('marker-end', 'url(#arrowhead)');

        // Draw nodes
        const node = this.svg.selectAll('.node')
            .data(this.nodes)
            .join('g')
            .attr('class', 'node')
            .call(d3.drag()
                .on('start', this.dragstarted.bind(this))
                .on('drag', this.dragged.bind(this))
                .on('end', this.dragended.bind(this)));

        // Add circles for nodes
        node.append('circle')
            .attr('r', 20)
            .attr('fill', d => this.getNodeColor(d.type));

        // Add labels
        node.append('text')
            .attr('dy', 30)
            .attr('text-anchor', 'middle')
            .text(d => d.id);

        // Update positions on tick
        this.simulation.on('tick', () => {
            link
                .attr('x1', d => d.source.x)
                .attr('y1', d => d.source.y)
                .attr('x2', d => d.target.x)
                .attr('y2', d => d.target.y);

            node
                .attr('transform', d => `translate(${d.x},${d.y})`);
        });
    }

    getNodeColor(type) {
        const colors = {
            'sender': '#4CAF50',
            'emulator': '#2196F3',
            'receiver': '#F44336'
        };
        return colors[type] || '#999';
    }

    dragstarted(event) {
        if (!event.active) this.simulation.alphaTarget(0.3).restart();
        event.subject.fx = event.subject.x;
        event.subject.fy = event.subject.y;
    }

    dragged(event) {
        event.subject.fx = event.x;
        event.subject.fy = event.y;
    }

    dragended(event) {
        if (!event.active) this.simulation.alphaTarget(0);
        event.subject.fx = null;
        event.subject.fy = null;
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
        workflowGraph.updateGraph(config.components, config.edges);
    } catch (error) {
        console.error('Failed to refresh workflow graph:', error);
    }
} 