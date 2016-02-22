import React, {PropTypes} from 'react';
import { Map, TileLayer, CircleMarker, Popup } from 'react-leaflet';

import 'leaflet/dist/leaflet.css';

const MapVisualization = React.createClass({
  propTypes: {
    id: PropTypes.string.isRequired,
    data: PropTypes.object,
    config: PropTypes.object.isRequired,
    height: PropTypes.number,
    width: PropTypes.number,
  },
  getDefaultProps() {
    return {
      data: {},
    };
  },
  position: [0, 0],
  // Coordinates are given as "lat,long"
  _formatMarker(coordinates, occurrences) {
    const formattedCoordinates = coordinates.split(',').map(component => Number(component));
    return (
      <CircleMarker key={coordinates} center={formattedCoordinates} radius={occurrences} color="#AF2228" fillColor="#D3242B" opacity={0.8}>
        <Popup>
            <dl>
              <dt>Coordinates:</dt>
              <dd>{coordinates}</dd>
              <dt>Number of occurrences:</dt>
              <dd>{occurrences}</dd>
            </dl>
        </Popup>
      </CircleMarker>
    );
  },
  render() {
    const coordinates = Object.keys(this.props.data);
    const markers = coordinates.map(aCoordinates => this._formatMarker(aCoordinates, this.props.data[aCoordinates]));

    return (
      <Map center={this.position} zoom={2} style={{height: 700, width: 1200}} scrollWheelZoom={false}>
        <TileLayer url={`https://api.mapbox.com/v4/mapbox.streets/{z}/{x}/{y}.png?access_token=${window.mapConfig.mapboxAccessToken}`}
                   maxZoom={19}
                   attribution={`<a href="http://www.mapbox.com/about/maps/" target="_blank">Terms &amp; Feedback</a>`}/>
        {markers}
      </Map>
    );
  },
});

export default MapVisualization;
