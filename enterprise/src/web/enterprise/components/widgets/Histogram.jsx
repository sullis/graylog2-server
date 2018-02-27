import React from 'react';
import PropTypes from 'prop-types';
import Immutable from 'immutable';
import moment from 'moment';

import Plotly from 'enterprise/custom-plotly';
import createPlotlyComponent from 'react-plotly.js/factory'
const Plot = createPlotlyComponent(Plotly);

const _formatTimestamp = (epoch) => {
  return moment.unix(epoch).format('YYYY-MM-DD HH:mm:ss');
};

const _generateSeries = (results) => {
  const data = new Immutable.OrderedMap(results);
  return [{
    type: 'bar',
    x: data.keySeq().map(_formatTimestamp).toArray(),
    y: data.valueSeq().toArray(),
    name: 'took_ms',
  }];
};

export default function Histogram(props) {
  return (<Plot data={_generateSeries(props.data.results)}
                fit
                layout={{
                  margin: {
                    t: 10,
                    pad: 10,
                  },
                }}
                config={{ displayModeBar: false }} />);
};

Histogram.propTypes = {
  data: PropTypes.shape({
    config: PropTypes.shape({
      timerange: PropTypes.object.isRequired,
    }).isRequired,
    interval: PropTypes.string.isRequired,
    timerange: PropTypes.object.isRequired,
    results: PropTypes.object.isRequired,
  }).isRequired,
};
