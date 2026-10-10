import { Tab, TabList } from "@fluentui/react-components";

export function AppTabs({
  id,
  label,
  tabs,
  selected,
  onSelected,
}: {
  id: string;
  label: string;
  tabs: readonly Readonly<{ id: string; label: string }>[];
  selected: string;
  onSelected: (id: string) => void;
}) {
  return (
    <TabList
      aria-label={label}
      selectedValue={selected}
      onTabSelect={(_event, data) => {
        if (typeof data.value === "string") onSelected(data.value);
      }}
    >
      {tabs.map((tab) => (
        <Tab key={tab.id} id={`${id}-tab-${tab.id}`} aria-controls={`${id}-panel`} value={tab.id}>
          {tab.label}
        </Tab>
      ))}
    </TabList>
  );
}
