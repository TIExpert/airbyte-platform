import React, { memo, useEffect } from "react";
import { useFormContext, useWatch } from "react-hook-form";
import { FormattedMessage, useIntl } from "react-intl";

import { Box } from "components/ui/Box";
import { Collapsible } from "components/ui/Collapsible";
import { FlexContainer } from "components/ui/Flex";
import { ExternalLink } from "components/ui/Link";
import { Message } from "components/ui/Message";
import { Spinner } from "components/ui/Spinner";
import { Text } from "components/ui/Text";

import { useOssSecurityCheck } from "core/api";
import { links } from "core/utils/links";
import { CheckBoxControl } from "packages/cloud/views/auth/components/CheckBoxControl";

import { SetupFormValues, SecurityCheckStatus } from "./SetupForm";

export const SecurityCheck: React.FC = memo(() => {
  const { formatMessage } = useIntl();
  const { setValue } = useFormContext<SetupFormValues>();
  const { data, isLoading, isError } = useOssSecurityCheck(window.location.origin);
  const securityCheck = useWatch<SetupFormValues>({ name: "securityCheck" });

  useEffect(() => {
    if (isLoading) {
      setValue("securityCheck", SecurityCheckStatus.loading);
    } else if (isError) {
      setValue("securityCheck", SecurityCheckStatus.check_failed);
    } else if (data?.status === "closed") {
      setValue("securityCheck", SecurityCheckStatus.succeeded);
    } else {
      setValue("securityCheck", SecurityCheckStatus.failed);
    }
  }, [data?.status, isError, isLoading, setValue]);

  if (isLoading) {
    return (
      <FlexContainer alignItems="center" data-testid="securityCheckRunning">
        <Spinner size="xs" />
        <Text>
          <FormattedMessage id="setupForm.check.loading" />
        </Text>
      </FlexContainer>
    );
  }

  if (data?.status === "closed") {
    return <Message type="info" text={formatMessage({ id: "setupForm.check.closed" })} />;
  }

  if (data?.status === "default_auth" || data?.status === "open") {
    return (
      <Message type="error" text={formatMessage({ id: "setupForm.check.unsecured.title" })}>
        <Box p="md">
          <Text>
            {data.status === "open" && (
              <Box mb="md">
                <FormattedMessage id="setupForm.check.unsecured.open" />
              </Box>
            )}
            {data.status === "default_auth" && (
              <Box mb="md">
                <FormattedMessage id="setupForm.check.unsecured.defaultAuth" />
              </Box>
            )}
            <Box mb="lg">
              <FormattedMessage
                id="setupForm.check.unsecured.hint"
                values={{
                  lnk: (node: React.ReactNode) => (
                    <ExternalLink href={links.ossSecurityDocs} variant="primary">
                      {node}
                    </ExternalLink>
                  ),
                }}
              />
            </Box>
          </Text>
          <Collapsible label={formatMessage({ id: "setupForm.check.advancedOptions" })} data-testid="advancedOptions">
            <CheckBoxControl
              label={formatMessage({ id: "setupForm.check.overwrite" })}
              data-testid="overwriteSecurityCheck"
              name="overwriteSecurityCheck"
              checked={securityCheck === SecurityCheckStatus.ignored}
              onChange={({ target: { checked } }) => {
                setValue("securityCheck", checked ? SecurityCheckStatus.ignored : SecurityCheckStatus.failed, {
                  shouldValidate: true,
                });
              }}
            />
          </Collapsible>
        </Box>
      </Message>
    );
  }

  return (
    <Message
      type="warning"
      text={formatMessage(
        { id: "setupForm.check.failed" },
        {
          lnk: (node: React.ReactNode) => (
            <ExternalLink href={links.ossSecurityDocs} variant="primary">
              {node}
            </ExternalLink>
          ),
        }
      )}
    />
  );
});
SecurityCheck.displayName = "SecurityCheck";
