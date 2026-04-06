export function joinTilesToBoundaries(boundaryCollection, tileCollection) {
  const tilesByBoundaryKey = new Map(
    (tileCollection?.features ?? [])
      .filter((feature) => feature?.properties?.boundaryKey)
      .map((feature) => [String(feature.properties.boundaryKey), feature.properties])
  );

  return {
    ...boundaryCollection,
    features: (boundaryCollection?.features ?? []).map((boundary) => {
      const boundaryKey = String(boundary?.id ?? boundary?.properties?.GEOID ?? "");
      const tileProperties = tilesByBoundaryKey.get(boundaryKey) ?? {};

      return {
        ...boundary,
        properties: {
          ...boundary.properties,
          ...tileProperties,
          boundaryKey,
        },
      };
    }),
  };
}
