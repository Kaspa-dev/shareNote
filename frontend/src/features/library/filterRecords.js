export function filterRecords(items, section, query, filter) {
  return items.filter((item) => {
    const search =
      `${item.title || item.name} ${item.content || item.description} ${item.category || ""}`.toLocaleLowerCase(
        "lt",
      );
    return (
      search.includes(query.toLocaleLowerCase("lt")) &&
      (!filter ||
        String(section === "notes" ? item.groupId : item.organizationId) ===
          filter)
    );
  });
}
