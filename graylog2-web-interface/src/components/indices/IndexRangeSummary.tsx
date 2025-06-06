/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
import React from 'react';

import RelativeTime from 'components/common/RelativeTime';

type IndexRangeSummaryProps = {
  indexRange?: any;
};

class IndexRangeSummary extends React.Component<
  IndexRangeSummaryProps,
  {
    [key: string]: any;
  }
> {
  render() {
    const { indexRange } = this.props;

    if (!indexRange) {
      return (
        <span>
          <i>No index range available.</i>
        </span>
      );
    }

    return (
      <span>
        Range re-calculated <RelativeTime dateTime={indexRange.calculated_at} /> in {indexRange.took_ms}ms.
      </span>
    );
  }
}

export default IndexRangeSummary;
