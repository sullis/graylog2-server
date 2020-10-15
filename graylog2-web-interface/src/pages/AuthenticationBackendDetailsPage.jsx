// @flow strict
import * as React from 'react';
import { useState, useEffect } from 'react';
import { withRouter } from 'react-router';
import { LinkContainer } from 'react-router-bootstrap';

import {} from 'components/authentication/bindings'; // Bind all authentication plugins
import DocsHelper from 'util/DocsHelper';
import StringUtils from 'util/StringUtils';
import AuthenticationDomain from 'domainActions/authentication/AuthenticationDomain';
import useActiveBackend from 'components/authentication/useActiveBackend';
import { Spinner, PageHeader, DocumentTitle } from 'components/common';
import BackendDetails from 'components/authentication/BackendDetails';
import BackendOverviewLinks from 'components/authentication/BackendOverviewLinks';
import DocumentationLink from 'components/support/DocumentationLink';
import Routes from 'routing/Routes';
import { Button } from 'components/graylog';

type Props = {
  params: {
    backendId: string,
  },
};

const _pageTilte = (authBackendTitle) => {
  const backendTitle = StringUtils.truncateWithEllipses(authBackendTitle, 30);

  return <>Authentication Service Details - <i>{backendTitle}</i></>;
};

const AuthenticationBackendDetailsPage = ({ params: { backendId } }: Props) => {
  const [authBackend, setAuthBackend] = useState();
  const { finishedLoading, activeBackend } = useActiveBackend();

  useEffect(() => {
    AuthenticationDomain.load(backendId).then((response) => response && setAuthBackend(response.backend));
  }, []);

  if (!authBackend) {
    return <Spinner />;
  }

  const pageTitle = _pageTilte(authBackend.title);

  return (
    <DocumentTitle title={pageTitle}>
      <>
        <PageHeader title={pageTitle}
                    subactions={(
                      <LinkContainer to={Routes.SYSTEM.AUTHENTICATION.BACKENDS.edit(authBackend?.id)}>
                        <Button bsStyle="success"
                                type="button">
                          Edit Service
                        </Button>
                      </LinkContainer>
                  )}>
          <span>Configure Graylog&apos;s authentication services of this Graylog cluster.</span>
          <span>Read more authentication in the <DocumentationLink page={DocsHelper.PAGES.USERS_ROLES}
                                                                   text="documentation" />.
          </span>
          <BackendOverviewLinks activeBackend={activeBackend}
                                finishedLoading={finishedLoading} />
        </PageHeader>
        <BackendDetails authenticationBackend={authBackend} />
      </>
    </DocumentTitle>
  );
};

export default withRouter(AuthenticationBackendDetailsPage);
