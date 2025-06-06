import classNames from "classnames";
import React, { useMemo } from "react";
import { useFormContext } from "react-hook-form";
import { FormattedMessage } from "react-intl";

import Indicator from "components/Indicator";
import { FlexContainer } from "components/ui/Flex";
import { Icon } from "components/ui/Icon";
import { Text } from "components/ui/Text";
import { InfoTooltip } from "components/ui/Tooltip";

import { useCustomComponentsEnabled } from "core/api";
import { Action, Namespace, useAnalyticsService } from "core/services/analytics";
import { BuilderView, useConnectorBuilderFormState } from "services/connectorBuilder/ConnectorBuilderStateService";

import { AddStreamButton } from "./AddStreamButton";
import styles from "./BuilderSidebar.module.scss";
import { Sidebar } from "../Sidebar";
import { useBuilderErrors } from "../useBuilderErrors";
import { useBuilderWatch } from "../useBuilderWatch";
import { useStreamTestMetadata } from "../useStreamTestMetadata";

interface ViewSelectButtonProps {
  className?: string;
  selected: boolean;
  showIndicator?: "warning" | "error";
  onClick: () => void;
  "data-testid": string;
}

const ViewSelectButton: React.FC<React.PropsWithChildren<ViewSelectButtonProps>> = ({
  children,
  className,
  selected,
  showIndicator,
  onClick,
  "data-testid": testId,
}) => {
  return (
    <button
      type="button"
      data-testid={testId}
      className={classNames(className, styles.viewButton, {
        [styles.selectedViewButton]: selected,
        [styles.unselectedViewButton]: !selected,
      })}
      onClick={onClick}
    >
      <div className={styles.viewLabel}>{children}</div>
      {showIndicator && (
        <Indicator
          className={classNames(styles.indicator, { [styles.warningIndicator]: showIndicator === "warning" })}
        />
      )}
    </button>
  );
};

interface StreamViewButtonProps {
  id: string;
  name: string;
  num: number;
  async: boolean;
}
const StreamViewButton: React.FC<StreamViewButtonProps> = ({ id, name, num, async }) => {
  const analyticsService = useAnalyticsService();
  const { hasErrors } = useBuilderErrors();
  const { setValue } = useFormContext();
  const view = useBuilderWatch("view");

  const { getStreamTestWarnings } = useStreamTestMetadata();
  const testWarnings = useMemo(() => getStreamTestWarnings(name, true), [getStreamTestWarnings, name]);

  return (
    <ViewSelectButton
      data-testid={`navbutton-${String(num)}`}
      selected={view === num}
      showIndicator={hasErrors([num]) ? "error" : testWarnings.length > 0 ? "warning" : undefined}
      onClick={() => {
        setValue("streamTab", "requester");
        setValue("view", num);
        analyticsService.track(Namespace.CONNECTOR_BUILDER, Action.STREAM_SELECT, {
          actionDescription: "Stream view selected",
          stream_id: id,
          stream_name: name,
        });
      }}
    >
      <FlexContainer className={styles.streamViewButtonContent} alignItems="center">
        {name && name.trim() ? (
          <Text className={styles.streamViewText}>{name}</Text>
        ) : (
          <Text className={styles.emptyStreamViewText}>
            <FormattedMessage id="connectorBuilder.emptyName" />
          </Text>
        )}
        {async && <Text className={styles.asyncBadge}>async</Text>}
      </FlexContainer>
    </ViewSelectButton>
  );
};

interface BuilderSidebarProps {
  className?: string;
}

export const BuilderSidebar: React.FC<BuilderSidebarProps> = () => {
  const analyticsService = useAnalyticsService();
  const { hasErrors } = useBuilderErrors();
  const { permission } = useConnectorBuilderFormState();
  const { setValue } = useFormContext();
  const formValues = useBuilderWatch("formValues");
  const view = useBuilderWatch("view");
  const handleViewSelect = (selectedView: BuilderView) => {
    setValue("view", selectedView);
  };

  const areCustomComponentsEnabled = useCustomComponentsEnabled();

  return (
    <Sidebar yamlSelected={false}>
      <FlexContainer direction="column" alignItems="stretch" gap="none">
        <ViewSelectButton
          data-testid="navbutton-global"
          selected={view === "global"}
          showIndicator={hasErrors(["global"]) ? "error" : undefined}
          onClick={() => {
            handleViewSelect("global");
            analyticsService.track(Namespace.CONNECTOR_BUILDER, Action.GLOBAL_CONFIGURATION_SELECT, {
              actionDescription: "Global Configuration view selected",
            });
          }}
        >
          <Icon type="parameters" />
          <Text className={styles.streamViewText}>
            <FormattedMessage id="connectorBuilder.globalConfiguration" />
          </Text>
        </ViewSelectButton>

        <ViewSelectButton
          data-testid="navbutton-inputs"
          selected={view === "inputs"}
          showIndicator={hasErrors(["inputs"]) ? "error" : undefined}
          onClick={() => {
            handleViewSelect("inputs");
            analyticsService.track(Namespace.CONNECTOR_BUILDER, Action.USER_INPUTS_SELECT, {
              actionDescription: "User Inputs view selected",
            });
          }}
        >
          <Icon type="user" />
          <Text className={styles.streamViewText}>
            <FormattedMessage
              id="connectorBuilder.userInputs"
              values={{
                number: formValues.inputs.filter(({ definition }) => !definition.airbyte_hidden).length,
              }}
            />
          </Text>
        </ViewSelectButton>

        {areCustomComponentsEnabled && (
          <ViewSelectButton
            data-testid="navbutton-components"
            selected={view === "components"}
            onClick={() => {
              handleViewSelect("components");
              analyticsService.track(Namespace.CONNECTOR_BUILDER, Action.COMPONENTS_SELECT, {
                actionDescription: "Components view selected",
              });
            }}
          >
            <Icon type="wrench" />
            <Text className={styles.streamViewText}>
              <FormattedMessage id="connectorBuilder.customComponents" />
            </Text>
          </ViewSelectButton>
        )}
      </FlexContainer>

      <FlexContainer direction="column" alignItems="stretch" gap="sm" className={styles.streamListContainer}>
        <FlexContainer className={styles.streamsHeader} alignItems="center" justifyContent="space-between">
          <FlexContainer alignItems="center" gap="none">
            <Text className={styles.streamsHeading} size="xs" bold>
              <FormattedMessage id="connectorBuilder.streamsHeading" values={{ number: formValues.streams.length }} />
            </Text>
            <InfoTooltip placement="top">
              <FormattedMessage id="connectorBuilder.streamTooltip" />
            </InfoTooltip>
          </FlexContainer>

          <AddStreamButton
            onAddStream={(addedStreamNum) => handleViewSelect(addedStreamNum)}
            disabled={permission === "readOnly"}
            data-testid="add-stream"
          />
        </FlexContainer>

        <div className={styles.streamList}>
          {formValues.streams.map(({ name, id, requestType }, num) => (
            <StreamViewButton key={num} id={id} name={name} num={num} async={requestType === "async"} />
          ))}
        </div>
      </FlexContainer>
    </Sidebar>
  );
};
