import type * as React from "react";

import { DisclosureTriangle } from "metabase/components/DisclosureTriangle";
import { useToggle } from "metabase/hooks/use-toggle";

import { CollapsibleSectionContent } from "./CustomFormSection.styled";

interface SectionProps {
  title?: string;
  children: React.ReactNode;
}

function StandardSection({ title, children, ...props }: SectionProps) {
  return (
    <section className="mb4" {...props}>
      {title && <h2 className="mb2">{title}</h2>}
      {children}
    </section>
  );
}

function CollapsibleSection({ title, children, ...props }: SectionProps) {
  const [isExpanded, { toggle: handleToggle }] = useToggle(false);
  return (
    <section className="mb4" {...props}>
      <CollapsibleSectionContent onClick={handleToggle}>
        <DisclosureTriangle
          name="expand_arrow"
          open={isExpanded}
          style={{ marginInlineEnd: "1rem" }}
        />
        <h3>{title}</h3>
      </CollapsibleSectionContent>
      <div className={isExpanded ? undefined : "hide"}>{children}</div>
    </section>
  );
}

interface CustomFormSectionProps extends SectionProps {
  collapsible?: boolean;
}

/**
 * @deprecated
 */
function CustomFormSection({ collapsible, ...props }: CustomFormSectionProps) {
  const Section = collapsible ? CollapsibleSection : StandardSection;
  return <Section {...props} />;
}

// eslint-disable-next-line import/no-default-export -- deprecated usage
export default CustomFormSection;
