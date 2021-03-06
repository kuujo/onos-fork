/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    HostListener,
    Input,
    OnChanges,
    OnInit,
    Output,
    QueryList,
    SimpleChanges,
    ViewChildren
} from '@angular/core';
import {IconService, LogService} from 'gui2-fw-lib';
import {
    Device,
    ForceDirectedGraph,
    Host, HostLabelToggle,
    LabelToggle,
    LayerType,
    Node,
    Region,
    RegionLink,
    SubRegion
} from './models';
import {DeviceNodeSvgComponent, HostNodeSvgComponent} from './visuals';


/**
 * ONOS GUI -- Topology Forces Graph Layer View.
 *
 * The regionData is set by Topology Service on WebSocket topo2CurrentRegion callback
 * This drives the whole Force graph
 */
@Component({
    selector: '[onos-forcesvg]',
    templateUrl: './forcesvg.component.html',
    styleUrls: ['./forcesvg.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForceSvgComponent implements OnInit, OnChanges {
    @Input() onosInstMastership: string = '';
    @Input() visibleLayer: LayerType = LayerType.LAYER_DEFAULT;
    @Output() linkSelected = new EventEmitter<RegionLink>();
    @Output() selectedNodeEvent = new EventEmitter<Node>();
    @Input() selectedLink: RegionLink = null;
    @Input() showHosts: boolean = false;
    @Input() deviceLabelToggle: LabelToggle = LabelToggle.NONE;
    @Input() hostLabelToggle: HostLabelToggle = HostLabelToggle.NONE;
    @Input() regionData: Region = <Region>{devices: [ [], [], [] ], hosts: [ [], [], [] ], links: []};
    private graph: ForceDirectedGraph;
    private _options: { width, height } = { width: 800, height: 600 };

    // References to the children of this component - these are created in the
    // template view with the *ngFor and we get them by a query here
    @ViewChildren(DeviceNodeSvgComponent) devices: QueryList<DeviceNodeSvgComponent>;
    @ViewChildren(HostNodeSvgComponent) hosts: QueryList<HostNodeSvgComponent>;

    constructor(
        protected log: LogService,
        protected is: IconService,
        private ref: ChangeDetectorRef
    ) {
        this.selectedLink = null;
        this.log.debug('ForceSvgComponent constructed');
    }

    /**
     * Utility for extracting a node name from an endpoint string
     * In some cases - have to remove the port number from the end of a device
     * name
     * @param endPtStr The end point name
     */
    private static extractNodeName(endPtStr: string): string {
        const slash: number = endPtStr.indexOf('/');
        if (slash === -1) {
            return endPtStr;
        } else {
            const afterSlash = endPtStr.substr(slash + 1);
            if (afterSlash === 'None') {
                return endPtStr;
            } else {
                return endPtStr.substr(0, slash);
            }
        }
    }

    @HostListener('window:resize', ['$event'])
    onResize(event) {
        this.graph.initSimulation(this.options);
        this.log.debug('Simulation reinit after resize', event);
    }

    /**
     * After the component is initialized create the Force simulation
     * The list of devices, hosts and links will not have been receieved back
     * from the WebSocket yet as this time - they will be updated later through
     * ngOnChanges()
     */
    ngOnInit() {
        // Receiving an initialized simulated graph from our custom d3 service
        this.graph = new ForceDirectedGraph(this.options);

        /** Binding change detection check on each tick
         * This along with an onPush change detection strategy should enforce
         * checking only when relevant! This improves scripting computation
         * duration in a couple of tests I've made, consistently. Also, it makes
         * sense to avoid unnecessary checks when we are dealing only with
         * simulations data binding.
         */
        this.graph.ticker.subscribe((simulation) => {
            // this.log.debug("Force simulation has ticked", simulation);
            this.ref.markForCheck();
        });
        this.log.debug('ForceSvgComponent initialized - waiting for nodes and links');

        this.is.loadIconDef('m_switch');
        this.is.loadIconDef('m_roadm');
        this.is.loadIconDef('m_router');
        this.is.loadIconDef('m_endstation');
    }

    /**
     * When any one of the inputs get changed by a containing component, this
     * gets called automatically. In addition this is called manually by
     * topology.service when a response is received from the WebSocket from the
     * server
     *
     * The Devices, Hosts and SubRegions are all added to the Node list for the simulation
     * The Links are added to the Link list of the simulation.
     * Before they are added the Links are associated with Nodes based on their endPt
     *
     * @param changes - a list of changed @Input(s)
     */
    ngOnChanges(changes: SimpleChanges) {
        if (changes['regionData']) {
            const devices: Device[] =
                changes['regionData'].currentValue.devices[this.visibleLayerIdx()];
            const hosts: Host[] =
                changes['regionData'].currentValue.hosts[this.visibleLayerIdx()];
            const subRegions: SubRegion[] = changes['regionData'].currentValue.subRegion;
            this.graph.nodes = [];
            if (devices) {
                this.graph.nodes = devices;
            }
            if (hosts) {
                this.graph.nodes = this.graph.nodes.concat(hosts);
            }
            if (subRegions) {
                this.graph.nodes = this.graph.nodes.concat(subRegions);
            }

            // Associate the endpoints of each link with a real node
            this.graph.links = [];
            for (const linkIdx of Object.keys(this.regionData.links)) {
                const epA = ForceSvgComponent.extractNodeName(
                                        this.regionData.links[linkIdx].epA);
                this.regionData.links[linkIdx].source =
                    this.graph.nodes.find((node) =>
                        node.id === epA);
                const epB = ForceSvgComponent.extractNodeName(
                    this.regionData.links[linkIdx].epB);
                this.regionData.links[linkIdx].target =
                    this.graph.nodes.find((node) =>
                        node.id === epB);
                this.regionData.links[linkIdx].index = Number(linkIdx);
            }

            this.graph.links = this.regionData.links;

            this.graph.initSimulation(this.options);
            this.graph.initNodes();
            this.log.debug('ForceSvgComponent input changed',
                this.graph.nodes.length, 'nodes,', this.graph.links.length, 'links');
        }

        if (changes['showHosts']) {
            this.showHosts = changes['showHosts'].currentValue;
        }

        // Pass on the changes to device
        if (changes['deviceLabelToggle']) {
            this.deviceLabelToggle = changes['deviceLabelToggle'].currentValue;
            this.devices.forEach((d) => {
                d.ngOnChanges({'labelToggle': changes['deviceLabelToggle']});
            });
        }

        // Pass on the changes to host
        if (changes['hostLabelToggle']) {
            this.hostLabelToggle = changes['hostLabelToggle'].currentValue;
            this.hosts.forEach((h) => {
                h.ngOnChanges({'labelToggle': changes['hostLabelToggle']});
            });
        }

        this.ref.markForCheck();
    }

    /**
     * Get the index of LayerType so it can drive the visibility of nodes and
     * hosts on layers
     */
    visibleLayerIdx(): number {
        const layerKeys: string[] = Object.keys(LayerType);
        for (const idx in layerKeys) {
            if (LayerType[layerKeys[idx]] === this.visibleLayer) {
                return Number(idx);
            }
        }
        return -1;
    }

    selectLink(link: RegionLink): void {
        this.selectedLink = link;
        this.linkSelected.emit(link);
    }

    get options() {
        return this._options = {
            width: window.innerWidth,
            height: window.innerHeight
        };
    }

    /**
     * Iterate through all hosts and devices to deselect the previously selected
     * node. The emit an event to the parent that lets it know the selection has
     * changed.
     * @param selectedNode the newly selected node
     */
    updateSelected(selectedNode: Node): void {
        this.log.debug('Device selected', selectedNode);
        this.devices
            .filter((d) =>
                selectedNode === undefined || d.device.id !== selectedNode.id)
            .forEach((d) => d.deselect());
        this.hosts
            .filter((h) =>
                selectedNode === undefined || h.host.id !== selectedNode.id)
            .forEach((h) => h.deselect());

        this.selectedNodeEvent.emit(selectedNode);
    }

    /**
     * We want to filter links to show only those not related to hosts if the
     * 'showHosts' flag has been switched off. If 'shwoHosts' is true, then
     * display all links.
     */
    filteredLinks() {
        return this.regionData.links.filter((h) =>
            this.showHosts ||
            ((<Host>h.source).nodeType !== 'host' &&
            (<Host>h.target).nodeType !== 'host'));
    }
}

